package som.primitives.threading;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.vm.Activity;
import som.vm.VmSettings;
import tools.concurrency.TracingActivityThread;
import tools.replay.ReplayData;
import tools.replay.ReplayRecord.AwaitTimeoutRecord;
import tools.replay.TraceRecord;
import tools.replay.actors.TracingLock.TracingCondition;
import tools.replay.nodes.RecordEventNodes.RecordTwoEvent;


public final class ConditionPrimitives {
  @GenerateNodeFactory
  @Primitive(primitive = "threadingSignalOne:")
  public abstract static class SignalOnePrim extends UnaryExpressionNode {
    @Child static protected RecordTwoEvent traceSignal =
        new RecordTwoEvent(TraceRecord.CONDITION_SIGNALONE);

    @Specialization
    @TruffleBoundary
    public final Condition doCondition(final Condition cond) {

      if (VmSettings.REPLAY) {
        ReplayData.replayDelayNumberedEvent((TracingCondition) cond,
            ((TracingCondition) cond).getId());
      }

      if (VmSettings.ACTOR_TRACING) {
        ((TracingCondition) cond).tracingSignal(traceSignal);
      } else {
        cond.signal();
      }

      return cond;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingSignalAll:")
  public abstract static class SignalAllPrim extends UnaryExpressionNode {
    @Child static protected RecordTwoEvent traceSignal =
        new RecordTwoEvent(TraceRecord.CONDITION_SIGNALALL);

    @Specialization
    @TruffleBoundary
    public final Condition doCondition(final Condition cond) {
      if (VmSettings.REPLAY) {
        ReplayData.replayDelayNumberedEvent((TracingCondition) cond,
            ((TracingCondition) cond).getId());
      }
      if (VmSettings.ACTOR_TRACING) {
        ((TracingCondition) cond).tracingSignalAll(traceSignal);
      } else {
        cond.signalAll();
      }
      return cond;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingAwait:")
  public abstract static class AwaitPrim extends UnaryExpressionNode {
    @Child static protected RecordTwoEvent traceSignal =
        new RecordTwoEvent(TraceRecord.CONDITION_WAIT);

    @Specialization
    @TruffleBoundary
    public final Condition doCondition(final Condition cond) {

      try {
        ObjectTransitionSafepoint.INSTANCE.unregister();
        if (VmSettings.REPLAY) {
          ReplayData.replayDelayNumberedEvent((TracingCondition) cond,
              ((TracingCondition) cond).getId());
        }

        if (VmSettings.ACTOR_TRACING) {
          ((TracingCondition) cond).tracingAwait(traceSignal);
        } else {
          cond.await();
        }
      } catch (InterruptedException e) {
        /* doesn't tell us a lot at the moment, so it is ignored */
      }

      ObjectTransitionSafepoint.INSTANCE.register();
      return cond;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingAwait:for:")
  public abstract static class AwaitForPrim extends BinaryExpressionNode {
    @Child static protected RecordTwoEvent traceSignal =
        new RecordTwoEvent(TraceRecord.CONDITION_AWAITTIMEOUT);
    @Child static protected RecordTwoEvent traceResult =
        new RecordTwoEvent(TraceRecord.CONDITION_AWAITTIMEOUT_RES);

    @Specialization
    @TruffleBoundary
    public final boolean doCondition(final Condition cond, final long milliseconds) {
      try {
        ObjectTransitionSafepoint.INSTANCE.unregister();

        try {
          if (VmSettings.REPLAY) {
            ReplayData.replayDelayNumberedEvent((TracingCondition) cond,
                ((TracingCondition) cond).getId());

            Activity reader = TracingActivityThread.currentThread().getActivity();
            AwaitTimeoutRecord atr = (AwaitTimeoutRecord) reader.getNextReplayEvent();

            if (atr.isSignaled) {
              // original did not time out and was signaled
              cond.await();
              return true;
            }

            // original timed out, we dont wait
            return false;
          } else if (VmSettings.ACTOR_TRACING) {
            boolean result = ((TracingCondition) cond).tracingAwait(traceSignal, milliseconds);
            traceResult.record(((TracingCondition) cond).getId(), result ? 1 : 0);
            return result;
          } else {
            return cond.await(milliseconds, TimeUnit.MILLISECONDS);
          }
        } catch (InterruptedException e) {
          if (VmSettings.ACTOR_TRACING) {
            traceResult.record(((TracingCondition) cond).getId(), 0);
          }
          return false;
        }
      } finally {
        ObjectTransitionSafepoint.INSTANCE.register();
      }
    }
  }
}
