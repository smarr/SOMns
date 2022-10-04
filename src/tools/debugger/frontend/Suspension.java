package tools.debugger.frontend;

import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.Method;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.actors.SuspendExecutionNode;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.primitives.ObjectPrims.HaltPrim;
import som.vm.Activity;
import som.vmobjects.SBlock;
import som.vmobjects.SObjectWithClass;
import tools.TraceData;
import tools.concurrency.TracingActivityThread;
import tools.debugger.FrontendConnector;
import tools.debugger.entities.EntityType;
import tools.debugger.entities.SteppingType;
import tools.debugger.frontend.ApplicationThreadStack.StackFrame;
import tools.debugger.frontend.ApplicationThreadTask.Resume;
import tools.debugger.frontend.ApplicationThreadTask.SendStackTrace;
import tools.debugger.message.VariablesRequest.FilterType;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;


/**
 * Suspension controls the interaction between the debugger front-end and the
 * application thread.
 *
 * <p>
 * It provides a simple mechanism to ask the application thread to perform
 * tasks on behalf of the front-end. This is useful for instance to walk the
 * stack and obtain the relevant data for the debugger.
 */
public class Suspension {
  public final long                                       activityId;
  private final Activity                                  activity;
  private final TracingActivityThread                     activityThread;
  private final ArrayBlockingQueue<ApplicationThreadTask> tasks;

  private SuspendedEvent         suspendedEvent;
  private ApplicationThreadStack stack;

  public Suspension(final TracingActivityThread activityThread,
      final Activity activity, final long activityId) {
    this.activityThread = activityThread;
    this.activity = activity;
    this.activityId = activityId;
    this.tasks = new ArrayBlockingQueue<>(2);
  }

  public synchronized void update(final SuspendedEvent e) {
    this.suspendedEvent = e;
    this.stack = new ApplicationThreadStack(this);
  }

  public synchronized ArrayList<StackFrame> getStackFrames() {
    return stack.get();
  }

  public EntityType[] getCurrentEntityScopes() {
    return activityThread.getConcurrentEntityScopes();
  }

  public synchronized long addScope(final Frame frame, final MethodScope lexicalScope) {
    return stack.addScope(frame, lexicalScope);
  }

  public synchronized long addObject(final Object obj) {
    return stack.addObject(obj);
  }

  /**
   * Get skipping frame count for the case when it is a HaltPrimitive or a
   * SuspendExecutionNode.
   */
  public synchronized int getFrameSkipCount() {
    int skipFrames = 0;
    if (suspendedEvent.getNode() instanceof HaltPrim) {
      return Suspension.FRAMES_SKIPPED_FOR_HALT;
    } else if (suspendedEvent.getNode() instanceof SuspendExecutionNode) {
      skipFrames = ((SuspendExecutionNode) suspendedEvent.getNode()).getSkipFrames();
    }
    return skipFrames;
  }

  public long getGlobalId(final int localId) {
    return TraceData.makeGlobalId(localId, activityId);
  }

  private int getLocalId(final long globalId) {
    assert TraceData.getActivityIdFromGlobalValId(
        globalId) == activityId : "should be an id for current activity, otherwise, something is wrong";
    return TraceData.valIdFromGlobal(globalId);
  }

  public synchronized StackFrame getFrame(final long globalId) {
    return stack.get().get(getLocalId(globalId));
  }

  public synchronized Object getScopeOrObject(final long globalVarRef) {
    return stack.getScopeOrObject(getLocalId(globalVarRef));
  }

  public static final int FRAMES_SKIPPED_FOR_HALT = 2;

  /**
   * Instruct the suspended thread to resume.
   */
  public void resume() {
    submitTask(new Resume());
  }

  /**
   * Instruct the suspended task to send its stack trace to the front-end.
   */
  public void sendStackTrace(final int startFrame, final int levels,
      final int requestId, final FrontendConnector frontend) {
    submitTask(new SendStackTrace(startFrame, levels, frontend, this, requestId));
  }

  public void sendScopes(final long frameId, final FrontendConnector frontend,
      final int requestId) {
    frontend.sendScopes(frameId, this, requestId);
  }

  public void sendVariables(final long varRef, final FrontendConnector frontend,
      final int requestId, final FilterType filter, final Long start, final Long count) {
    frontend.sendVariables(varRef, requestId, this, filter, start, count);
  }

  private void submitTask(final ApplicationThreadTask task) {
    try {
      tasks.put(task);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Suspend the current thread, and process tasks from the front-end.
   */
  public void suspend() {
    // don't participate in safepoints while being suspended
    ObjectTransitionSafepoint.INSTANCE.unregister();

    try {
      activityThread.markThreadAsSuspendedInDebugger();
      suspendWithoutObjectSafepoints();
    } finally {
      activityThread.markThreadAsResumedFromDebugger();
      ObjectTransitionSafepoint.INSTANCE.register();
    }
  }

  public void suspendWithoutObjectSafepoints() {
    boolean continueWaiting = true;
    while (continueWaiting) {
      try {
        continueWaiting = tasks.take().execute();

        if (!continueWaiting) {
          if (activityThread.isStepping(SteppingType.RETURN_FROM_ACTIVITY)) {
            activity.setStepToJoin(true);
          } else if (activityThread.isStepping(SteppingType.STEP_TO_NEXT_TURN)) {
            activity.setStepToNextTurn(true);
          } else if (activityThread.isStepping(
              SteppingType.RETURN_FROM_TURN_TO_PROMISE_RESOLUTION)) {
            assert activity instanceof Actor;
            EventualMessage turnMessage = EventualMessage.getCurrentExecutingMessage();
            SResolver resolver = turnMessage.getResolver();
            if (resolver != null) {
              resolver.getPromise().enableHaltOnResolution();
            }
          } else if (activityThread.isStepping(
              SteppingType.STEP_TO_END_TURN)) {
            assert activity instanceof Actor;
            EventualMessage turnMessage = EventualMessage.getCurrentExecutingMessage();
            String actorName = "";
            String turnName = "";

            Object[] args = turnMessage.getArgs();
            if (args.length > 0 && args[0] instanceof SObjectWithClass) { // e.g.
                                                                          // PromiseSendMessage,
                                                                          // DirectMessage
              final SObjectWithClass actorObject = (SObjectWithClass) args[0];
              actorName = actorObject.getSOMClass().getName().getString();
            }
            if (args.length > 0 && args[0] instanceof SBlock) { // e.g. PromiseCallbackMessage
              SBlock block = (SBlock) args[0];
              turnName = block.getMethod().getInvokable().getName();
            }

            if (turnName.isEmpty()) {
              if (actorName.isEmpty()) {
                turnName = turnMessage.getSelector().getString();
              } else {
                turnName =
                    actorName.concat(">>#").concat(turnMessage.getSelector().getString());
              }
            }

            Node suspendedNode = this.getEvent().getNode();
            ArrayList<StackFrame> stackFrames = this.getStackFrames();
            ArrayList<RootNode> rootNodeFrames = new ArrayList<>();
            // get root nodes for the frames in the stack
            for (StackFrame frame : stackFrames) {
              rootNodeFrames.add(frame.getRootNode());
            }

            this.getEvent().getSession().prepareStepEndTurn(Thread.currentThread(),
                suspendedNode, turnName, rootNodeFrames);
          }
        }
      } catch (InterruptedException e) {
        /* Just continue waiting */ }
    }
    synchronized (this) {
      suspendedEvent = null;
      stack = null;
    }
  }

  public TracingActivityThread getActivityThread() {
    return activityThread;
  }

  public Activity getActivity() {
    return activity;
  }

  public synchronized SuspendedEvent getEvent() {
    return suspendedEvent;
  }
}
