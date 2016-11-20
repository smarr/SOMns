package tools.debugger;

import java.lang.ref.WeakReference;
import java.util.concurrent.ArrayBlockingQueue;

import com.oracle.truffle.api.debug.SuspendedEvent;

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
  private final WeakReference<Object> activity;
  private final ArrayBlockingQueue<Task> tasks;
  private SuspendedEvent suspendedEvent;

  Suspension(final WeakReference<Object> activity, final int activityId) {
    this.activity   = activity;
    this.activityId = activityId;
    this.tasks = new ArrayBlockingQueue<>(2);
  }

  synchronized void update(final SuspendedEvent e) {
    this.suspendedEvent = e;
  }

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

  private void submitTask(final Task task) {
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
  }

  public Object getActivity() { return activity.get(); }
  public synchronized SuspendedEvent getEvent() { return suspendedEvent; }

  private abstract static class Task {
    /**
     * @return continue waiting on true, resume execution on false.
     */
    abstract boolean execute();
  }

  private static class Resume extends Task {
    @Override
    boolean execute() { return false; }
  }

  private static class SendStackTrace extends Task {
    private final int startFrame;
    private final int levels;
    private final int requestId;
    private final FrontendConnector frontend;
    private final Suspension suspension;

    SendStackTrace(final int startFrame, final int levels,
        final FrontendConnector frontend, final Suspension suspension,
        final int requestId) {
      this.startFrame = startFrame;
      this.levels     = levels;
      this.frontend   = frontend;
      this.suspension = suspension;
      this.requestId  = requestId;
    }

    @Override
    boolean execute() {
      frontend.sendStackTrace(startFrame, levels, suspension, requestId);
      return true;
    }
  }
}
