package som.vm;

import java.util.LinkedList;
import java.util.Queue;

import tools.debugger.entities.ActivityType;
import tools.replay.ReplayRecord;
import tools.replay.TraceParser;


public interface Activity {
  String getName();

  /**
   * The activity id.
   * For non-tracing activities, the id is 0.
   */
  default long getId() {
    return 0;
  }

  /**
   * Returns an id for external data.
   * Implementation should be synchronized, can't add that to the default though.
   */
  default int getDataId() {
    return 0;
  }

  ActivityType getType();

  default TraceParser getTraceParser() {
    assert VmSettings.REPLAY;
    throw new UnsupportedOperationException();
  }

  default ReplayRecord getNextReplayEvent() {

    Queue<ReplayRecord> q = getReplayEventBuffer();
    synchronized (q) {
      if (q.isEmpty()) {
        boolean more = getTraceParser().getMoreEventsForEntity(getId());
        while (q.isEmpty() && more) {
          more = getTraceParser().getMoreEventsForEntity(getId());
        }
      }
      return q.remove();
    }
  }

  default ReplayRecord peekNextReplayEvent() {
    Queue<ReplayRecord> q = getReplayEventBuffer();
    synchronized (q) {
      if (q.isEmpty()) {
        boolean more = getTraceParser().getMoreEventsForEntity(getId());
        while (q.isEmpty() && more) {
          more = getTraceParser().getMoreEventsForEntity(getId());
        }
      }

      return q.peek();
    }
  }

  default LinkedList<ReplayRecord> getReplayEventBuffer() {
    return null;
  }

  /**
   * The id for the next trace buffer used with this activity.
   * Since an activity is executed by at most one thread at a time,
   * this id allows us to order trace buffer fragments.
   * Non-tracing activities don't need to implement it.
   */
  default int getNextTraceBufferId() {
    throw new UnsupportedOperationException("Should never be called");
  }

  /**
   * Set the flag that indicates a breakpoint on joining activity.
   * Does nothing for non-tracing activities, i.e., when debugging is disabled.
   */
  default void setStepToJoin(final boolean val) {}

  void setStepToNextTurn(boolean val);
}
