package tools.debugger.frontend;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.MaterializedFrame;

import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.primitives.ObjectPrims.HaltPrim;
import tools.TraceData;
import tools.debugger.FrontendConnector;
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
  private final Object activity;
  private final ArrayBlockingQueue<ApplicationThreadTask> tasks;

  private SuspendedEvent suspendedEvent;
  private ApplicationThreadStack stack;

  public Suspension(final Object activity, final long activityId) {
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

  public synchronized long addScope(final MaterializedFrame frame,
      final MethodScope lexicalScope) {
    return stack.addScope(frame, lexicalScope);
  }

  public synchronized long addObject(final Object obj) {
    return stack.addObject(obj);
  }

  public synchronized boolean isHaltPrimitive() {
    return suspendedEvent.getNode() instanceof HaltPrim;
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
      } catch (InterruptedException e) { /* Just continue waiting */ }
    }
    synchronized (this) {
      suspendedEvent = null;
      stack = null;
    }

    ObjectTransitionSafepoint.INSTANCE.register();
  }

  public Object getActivity() { return activity; }
  public synchronized SuspendedEvent getEvent() { return suspendedEvent; }
}
