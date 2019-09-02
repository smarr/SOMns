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
import tools.replay.ReplayRecord.AwaitTimeoutRecord;
import tools.replay.TraceRecord;
import tools.replay.actors.TracingLock.TracingCondition;
import tools.replay.nodes.RecordEventNodes.RecordOneEvent;
import tools.replay.nodes.RecordEventNodes.RecordTwoEvent;


public final class ConditionPrimitives {
  @GenerateNodeFactory
  @Primitive(primitive = "threadingSignalOne:")
  public abstract static class SignalOnePrim extends UnaryExpressionNode {

    @Specialization
    @TruffleBoundary
    public final Condition doCondition(final Condition cond) {
      cond.signal();
      return cond;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingSignalAll:")
  public abstract static class SignalAllPrim extends UnaryExpressionNode {

    @Specialization
    @TruffleBoundary
    public final Condition doCondition(final Condition cond) {
      cond.signalAll();
      return cond;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingAwait:")
  public abstract static class AwaitPrim extends UnaryExpressionNode {

    @Child protected static RecordTwoEvent traceWakeup =
        new RecordTwoEvent(TraceRecord.CONDITION_WAKEUP);

    @Specialization
    @TruffleBoundary
    public final Condition doCondition(final Condition cond) {
      long aid = TracingActivityThread.currentThread().getActivity().getId();

      try {
        ObjectTransitionSafepoint.INSTANCE.unregister();
        cond.await();
        if (VmSettings.ACTOR_TRACING) {
          TracingCondition tc = (TracingCondition) cond;
          traceWakeup.record(tc.owner.getId(), tc.owner.getNextEventNumber());
          tc.owner.replayIncrementEventNo();
        }

        // Output.println("Activity " + aid + " woke up");
      } catch (InterruptedException e) {
        /* doesn't tell us a lot at the moment, so it is ignored */
      } finally {
        ObjectTransitionSafepoint.INSTANCE.register();
      }
      return cond;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingAwait:for:")
  public abstract static class AwaitForPrim extends BinaryExpressionNode {
    @Child protected static RecordOneEvent traceResult =
        new RecordOneEvent(TraceRecord.CONDITION_AWAITTIMEOUT_RES);
    @Child protected static RecordTwoEvent traceWakeup =
        new RecordTwoEvent(TraceRecord.CONDITION_WAKEUP);

    @Specialization
    @TruffleBoundary
    public final boolean doCondition(final Condition cond, final long milliseconds) {
      try {
        ObjectTransitionSafepoint.INSTANCE.unregister();

        try {
          if (VmSettings.REPLAY) {
            Activity reader = TracingActivityThread.currentThread().getActivity();
            AwaitTimeoutRecord atr = (AwaitTimeoutRecord) reader.getNextReplayEvent();

            if (atr.isSignaled) {
              // original did not time out and was signaled
              cond.await();
              return true;
            }

            // original timed out, we dont wait
            return false;
          } else {
            boolean result = cond.await(milliseconds, TimeUnit.MILLISECONDS);

            if (VmSettings.ACTOR_TRACING) {
              traceResult.record(result ? 1 : 0);
              if (result) {
                TracingCondition tc = (TracingCondition) cond;
                traceWakeup.record(tc.owner.getId(), tc.owner.getNextEventNumber());
                tc.owner.replayIncrementEventNo();
              }
            }

            return result;
          }
        } catch (InterruptedException e) {
          if (VmSettings.ACTOR_TRACING) {
            traceResult.record(0);
          }
          return false;
        }
      } finally {
        ObjectTransitionSafepoint.INSTANCE.register();
      }
    }
  }
}
