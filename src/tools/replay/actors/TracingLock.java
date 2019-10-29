package tools.replay.actors;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import som.vm.VmSettings;
import tools.concurrency.TracingActivityThread;
import tools.replay.PassiveEntityWithEvents;
import tools.replay.ReplayRecord.NumberedPassiveRecord;
import tools.replay.nodes.RecordEventNodes.RecordTwoEvent;


public final class TracingLock extends ReentrantLock implements PassiveEntityWithEvents {
  private static final long serialVersionUID = -3346973644925799901L;
  final long                id;
  volatile int              eventNo          = 0;
  public final Condition    replayCondition;

  public TracingLock() {
    id = TracingActivityThread.newEntityId();
    if (VmSettings.REPLAY) {
      replayCondition = super.newCondition();
    } else {
      replayCondition = null;
    }
  }

  public long getId() {
    return id;
  }

  public synchronized boolean tracingIsLocked(final RecordTwoEvent traceIsLocked) {
    boolean isLocked = isLocked();
    traceIsLocked.record(id, isLocked ? 1 : 0);
    return isLocked;
  }

  public void replayIncrementEventNo() {
    this.eventNo++;
  }

  @Override
  public Condition newCondition() {
    return new TracingCondition(this, super.newCondition());
  }

  public synchronized void tracingLock(final RecordTwoEvent traceLock) {
    lock();
    traceLock.record(id, eventNo);
    eventNo++;
  }

  @Override
  public int getNextEventNumber() {
    return eventNo;
  }

  public static final class TracingCondition implements Condition {
    public final TracingLock owner;
    final Condition          wrapped;

    public TracingCondition(final TracingLock owner, final Condition wrapped) {
      this.owner = owner;
      this.wrapped = wrapped;
    }

    @Override
    public void await() throws InterruptedException {
      wrapped.await();

      if (VmSettings.REPLAY) {
        NumberedPassiveRecord npr =
            (NumberedPassiveRecord) TracingActivityThread.currentThread().getActivity()
                                                         .getNextReplayEvent();

        while (owner.getNextEventNumber() != npr.eventNo) {
          owner.replayCondition.await();
        }

        owner.replayIncrementEventNo();
        owner.replayCondition.signalAll();
      }
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
      return awaitUntil(deadline);
    }

    @Override
    public void signal() {
      wrapped.signal();
    }

    @Override
    public void signalAll() {
      wrapped.signalAll();
    }
  }
}
