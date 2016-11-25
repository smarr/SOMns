package tools.debugger.frontend;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.MaterializedFrame;

import som.interpreter.LexicalScope.MethodScope;
import som.primitives.ObjectPrims.HaltPrim;
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
  public final int activityId;
  private final Object activity;
  private final ArrayBlockingQueue<ApplicationThreadTask> tasks;

  private SuspendedEvent suspendedEvent;
  private ApplicationThreadStack stack;

  public Suspension(final Object activity, final int activityId) {
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

  public synchronized int addScope(final MaterializedFrame frame,
      final MethodScope lexicalScope) {
    return stack.addScope(frame, lexicalScope);
  }

  public synchronized int addObject(final Object obj) {
    return stack.addObject(obj);
  }

  public synchronized boolean isHaltPrimitive() {
    return suspendedEvent.getNode() instanceof HaltPrim;
  }

  private static final int BITS_FOR_LOCAL_ID = 20;
  private static final int LOCAL_ID_MASK = (1 << BITS_FOR_LOCAL_ID) - 1;

  public int getGlobalId(final int localId) {
    final int maxId = 1 << BITS_FOR_LOCAL_ID;
    assert localId < maxId;
    assert ((long) activityId << BITS_FOR_LOCAL_ID) < Integer.MAX_VALUE;
    return (activityId << BITS_FOR_LOCAL_ID) + localId;
  }

  private int getLocalId(final int globalId) {
    assert (globalId >> BITS_FOR_LOCAL_ID) == activityId :
      "should be an id for current activity, otherwise, something is wrong";
    return globalId & LOCAL_ID_MASK;
  }

  public static int getActivityIdFromGlobalId(final int globalId) {
    return globalId >> BITS_FOR_LOCAL_ID;
  }

  public synchronized DebugStackFrame getFrame(final int globalId) {
    return stack.get().get(getLocalId(globalId));
  }

  public synchronized Object getScopeOrObject(final int globalVarRef) {
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

  public void sendScopes(final int frameId, final FrontendConnector frontend,
      final int requestId) {
    frontend.sendScopes(frameId, this, requestId);
  }

  public void sendVariables(final int varRef, final FrontendConnector frontend, final int requestId) {
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
  }

  public Object getActivity() { return activity; }
  public synchronized SuspendedEvent getEvent() { return suspendedEvent; }
}
