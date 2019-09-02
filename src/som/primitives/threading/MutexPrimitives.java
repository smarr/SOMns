package som.primitives.threading;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.instrumentation.Tag;

import bd.primitives.Primitive;
import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.vm.Activity;
import som.vm.VmSettings;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import tools.concurrency.Tags.AcquireLock;
import tools.concurrency.Tags.ExpressionBreakpoint;
import tools.concurrency.Tags.ReleaseLock;
import tools.concurrency.TracingActivityThread;
import tools.replay.ReplayData;
import tools.replay.ReplayRecord.IsLockedRecord;
import tools.replay.actors.ActorExecutionTrace;
import tools.replay.actors.TracingLock;
import tools.replay.actors.TracingLock.TracingCondition;
import tools.replay.nodes.RecordEventNodes.RecordOneEvent;
import tools.replay.nodes.RecordEventNodes.RecordTwoEvent;


public final class MutexPrimitives {
  @GenerateNodeFactory
  @Primitive(primitive = "threadingLock:", selector = "lock")
  public abstract static class LockPrim extends UnaryExpressionNode {
    @Child static protected RecordTwoEvent traceLock =
        new RecordTwoEvent(ActorExecutionTrace.LOCK_LOCK);

    @TruffleBoundary
    @Specialization
    public static final ReentrantLock lock(final ReentrantLock lock) {
      try {
        ObjectTransitionSafepoint.INSTANCE.unregister();
        if (VmSettings.REPLAY) {
          ReplayData.replayDelayNumberedEvent((TracingLock) lock,
              ((TracingLock) lock).getId());
        }

        if (VmSettings.ACTOR_TRACING) {
          ((TracingLock) lock).tracingLock(traceLock);
        } else {
          lock.lock();
        }
      } finally {
        ObjectTransitionSafepoint.INSTANCE.register();
      }
      return lock;
    }

    @Override
    protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
      if (tag == AcquireLock.class || tag == ExpressionBreakpoint.class) {
        return true;
      } else {
        return super.hasTagIgnoringEagerness(tag);
      }
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingUnlock:", selector = "unlock")
  public abstract static class UnlockPrim extends UnaryExpressionNode {
    @TruffleBoundary
    @Specialization
    public static final ReentrantLock unlock(final ReentrantLock lock) {
      lock.unlock();
      if (VmSettings.REPLAY) {
        ((TracingLock) lock).replayIncrementEventNo();
      }
      return lock;
    }

    @Override
    protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
      if (tag == ReleaseLock.class || tag == ExpressionBreakpoint.class) {
        return true;
      } else {
        return super.hasTagIgnoringEagerness(tag);
      }
    }
  }

  @GenerateNodeFactory
  @Primitive(selector = "critical:", receiverType = ReentrantLock.class)
  public abstract static class CritialPrim extends BinaryExpressionNode {
    @Child protected BlockDispatchNode dispatchBody = BlockDispatchNodeGen.create();

    @Specialization
    public Object critical(final ReentrantLock lock, final SBlock block) {
      LockPrim.lock(lock);
      try {
        return dispatchBody.executeDispatch(new Object[] {block});
      } finally {
        UnlockPrim.unlock(lock);
      }
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingIsLocked:")
  public abstract static class IsLockedPrim extends UnaryExpressionNode {
    @Child protected RecordTwoEvent traceIsLocked =
        new RecordTwoEvent(ActorExecutionTrace.LOCK_ISLOCKED);

    @Specialization
    @TruffleBoundary
    public boolean doLock(final ReentrantLock lock) {
      if (VmSettings.REPLAY) {
        Activity reader = TracingActivityThread.currentThread().getActivity();
        IsLockedRecord ilr = (IsLockedRecord) reader.getNextReplayEvent();
        assert ilr.lockId == ((TracingLock) lock).getId();
        return ilr.isLocked;
      } else if (VmSettings.ACTOR_TRACING) {
        return ((TracingLock) lock).tracingIsLocked(traceIsLocked);
      }

      return lock.isLocked();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingConditionFor:")
  public abstract static class ConditionForPrim extends UnaryExpressionNode {
    @Child RecordOneEvent trace = new RecordOneEvent(ActorExecutionTrace.CONDITION_CREATE);

    @Specialization
    @TruffleBoundary
    public Condition doLock(final ReentrantLock lock) {
      if (VmSettings.ACTOR_TRACING || VmSettings.REPLAY) {
        TracingCondition result = new TracingCondition(lock.newCondition());
        if (VmSettings.ACTOR_TRACING) {
          trace.record(result.getId());
        }
        return result;
      }
      return lock.newCondition();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingMutexNew:")
  public abstract static class MutexNewPrim extends UnaryExpressionNode {
    @Child RecordOneEvent trace = new RecordOneEvent(ActorExecutionTrace.LOCK_CREATE);

    // TODO: should I guard this on the mutex class?
    @Specialization
    @TruffleBoundary
    public final ReentrantLock doSClass(final SClass clazz) {
      if (VmSettings.ACTOR_TRACING || VmSettings.REPLAY) {
        TracingLock result = new TracingLock();
        if (VmSettings.ACTOR_TRACING) {
          trace.record(result.getId());
        }
        return result;
      }
      return new ReentrantLock();
    }
  }
}
