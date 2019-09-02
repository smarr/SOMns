package tools.replay.actors;

import java.util.concurrent.locks.ReentrantLock;

import tools.concurrency.TracingActivityThread;
import tools.replay.PassiveEntityWithEvents;
import tools.replay.nodes.RecordEventNodes.RecordTwoEvent;


public final class TracingLock extends ReentrantLock implements PassiveEntityWithEvents {
  private static final long serialVersionUID = -3346973644925799901L;
  long                      id;
  int                       eventNo          = 0;

  public TracingLock() {
    id = TracingActivityThread.newEntityId();
  }

  public long getId() {
    return id;
  }

  public final synchronized boolean tracingIsLocked(final RecordTwoEvent traceIsLocked) {
    boolean isLocked = isLocked();
    traceIsLocked.record(id, isLocked ? 1 : 0);
    return isLocked;
  }

  public final synchronized void tracingLock(final RecordTwoEvent traceLock) {
    traceLock.record(id, eventNo);
    eventNo++;
    lock();
  }

  @Override
  public int getNextEventNumber() {
    return eventNo;
  }
}
