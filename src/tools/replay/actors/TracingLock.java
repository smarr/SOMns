package tools.replay.actors;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
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

  public static final class TracingCondition implements PassiveEntityWithEvents, Condition {
    Condition wrapped;
    long      id;
    int       eventNo = 0;

    public TracingCondition(final Condition c) {
      this.wrapped = c;
      this.id = TracingActivityThread.newEntityId();
    }

    public long getId() {
      return id;
    }

    @Override
    public void await() throws InterruptedException {
      wrapped.await();
    }

    @Override
    public void awaitUninterruptibly() {
      wrapped.awaitUninterruptibly();
    }

    @Override
    public long awaitNanos(final long nanosTimeout) throws InterruptedException {
      return wrapped.awaitNanos(nanosTimeout);
    }

    @Override
    public boolean await(final long time, final TimeUnit unit) throws InterruptedException {
      return wrapped.await(time, unit);
    }

    @Override
    public boolean awaitUntil(final Date deadline) throws InterruptedException {
      return wrapped.awaitUntil(deadline);
    }

    @Override
    public void signal() {
      wrapped.signal();
    }

    @Override
    public void signalAll() {
      wrapped.signalAll();
    }

    @Override
    public int getNextEventNumber() {
      return eventNo;
    }

    public synchronized void tracingSignal(final RecordTwoEvent traceSignal) {
      traceSignal.record(id, eventNo);
      eventNo++;
      signal();
    }

    public synchronized void tracingSignalAll(final RecordTwoEvent traceSignal) {
      traceSignal.record(id, eventNo);
      eventNo++;
      signalAll();
    }

    public synchronized void tracingAwait(final RecordTwoEvent traceSignal)
        throws InterruptedException {
      traceSignal.record(id, eventNo);
      eventNo++;
      await();
    }

    public synchronized boolean tracingAwait(final RecordTwoEvent traceSignal,
        final long milliseconds) throws InterruptedException {
      traceSignal.record(id, eventNo);
      eventNo++;
      return await(milliseconds, TimeUnit.MILLISECONDS);
    }
  }
}
