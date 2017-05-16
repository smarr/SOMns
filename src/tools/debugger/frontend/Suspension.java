package tools.debugger.frontend;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.MaterializedFrame;

import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.actors.SuspendExecutionNode;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.primitives.ObjectPrims.HaltPrim;
import som.vm.Activity;
import tools.TraceData;
import tools.concurrency.TracingActivityThread;
import tools.debugger.FrontendConnector;
import tools.debugger.entities.EntityType;
import tools.debugger.entities.SteppingType;
import tools.debugger.frontend.ApplicationThreadTask.Resume;
import tools.debugger.frontend.ApplicationThreadTask.SendStackTrace;


/**
 * Suspension controls the interaction between the debugger front-end and the
 * application thread.
 *
 * <p>It provides a simple mechanism to ask the application thread to perform
 * tasks on behalf of the front-end. This is useful for instance to walk the
 * stack and obtain the relevant data for the debugger.
 */
public class Suspension {
  public final long activityId;
  private final Activity activity;
  private final TracingActivityThread activityThread;
  private final ArrayBlockingQueue<ApplicationThreadTask> tasks;

  private SuspendedEvent suspendedEvent;
  private ApplicationThreadStack stack;

  public Suspension(final TracingActivityThread activityThread,
      final Activity activity, final long activityId) {
    this.activityThread = activityThread;
    this.activity   = activity;
    this.activityId = activityId;
    this.tasks = new ArrayBlockingQueue<>(2);
  }

  public synchronized void update(final SuspendedEvent e) {
    this.suspendedEvent = e;
    this.stack = new ApplicationThreadStack(e, this);
  }

  public synchronized ArrayList<DebugStackFrame> getStackFrames() {
    return stack.get();
  }

  public EntityType[] getCurrentEntityScopes() {
    return activityThread.getConcurrentEntityScopes();
  }

  public synchronized long addScope(final MaterializedFrame frame,
      final MethodScope lexicalScope) {
    return stack.addScope(frame, lexicalScope);
  }

  public synchronized long addObject(final Object obj) {
    return stack.addObject(obj);
  }

  /**
   * Get skipping frame count for the case when it is a HaltPrimitive or a SuspendExecutionNode.
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
    assert TraceData.getActivityIdFromGlobalValId(globalId) == activityId :
      "should be an id for current activity, otherwise, something is wrong";
    return TraceData.valIdFromGlobal(globalId);
  }

  public synchronized DebugStackFrame getFrame(final long globalId) {
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

  public void sendVariables(final long varRef, final FrontendConnector frontend, final int requestId) {
    frontend.sendVariables(varRef, requestId, this);
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

    boolean continueWaiting = true;
    while (continueWaiting) {
      try {
        continueWaiting = tasks.take().execute();

        if (!continueWaiting) {
          if (activityThread.isStepping(SteppingType.RETURN_FROM_ACTIVITY)) {
            activity.setStepToJoin(true);
          } else if (activityThread.isStepping(SteppingType.STEP_TO_NEXT_TURN)) {
            activity.setStepToNextTurn(true);
          } else if (activityThread.isStepping(SteppingType.RETURN_FROM_TURN_TO_PROMISE_RESOLUTION)) {
            assert activity instanceof Actor;
            EventualMessage turnMessage = EventualMessage.getCurrentExecutingMessage();
            SResolver resolver = turnMessage.getResolver();
            if (resolver != null) {
              resolver.getPromise().setTriggerStopBeforeExecuteCallback(true);
            }
          }
        }
      } catch (InterruptedException e) { /* Just continue waiting */ }
    }
    synchronized (this) {
      suspendedEvent = null;
      stack = null;
    }

    ObjectTransitionSafepoint.INSTANCE.register();
  }

  public TracingActivityThread getActivityThread() { return activityThread; }
  public Activity getActivity() { return activity; }
  public synchronized SuspendedEvent getEvent() { return suspendedEvent; }
}
