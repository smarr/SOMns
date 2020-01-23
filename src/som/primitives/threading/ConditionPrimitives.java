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
import som.vm.VmSettings;
import tools.replay.TraceRecord;
import tools.replay.actors.TracingLock.TracingCondition;
import tools.replay.nodes.RecordEventNodes.RecordOneEvent;


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

    @Child protected static RecordOneEvent traceWakeup;

    public AwaitPrim() {
      if (VmSettings.ACTOR_TRACING) {
        traceWakeup = new RecordOneEvent(TraceRecord.CONDITION_WAKEUP);
      }
    }

    @Specialization
    @TruffleBoundary
    public final Condition doCondition(final Condition cond) {

      try {
        ObjectTransitionSafepoint.INSTANCE.unregister();
        cond.await();
        if (VmSettings.ACTOR_TRACING) {
          TracingCondition tc = (TracingCondition) cond;
          traceWakeup.record(tc.owner.getNextEventNumber());
          tc.owner.replayIncrementEventNo();
        }
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
    @Child protected static RecordOneEvent traceTimeout;
    @Child protected static RecordOneEvent traceWakeup;

    public AwaitForPrim() {
      if (VmSettings.ACTOR_TRACING) {
        traceTimeout = new RecordOneEvent(TraceRecord.CONDITION_TIMEOUT);
        traceWakeup = new RecordOneEvent(TraceRecord.CONDITION_WAKEUP);
      }
    }

    @Specialization
    @TruffleBoundary
    public final boolean doCondition(final Condition cond, final long milliseconds) {
      try {
        ObjectTransitionSafepoint.INSTANCE.unregister();

        try {
          boolean result = cond.await(milliseconds, TimeUnit.MILLISECONDS);

          if (VmSettings.ACTOR_TRACING) {
            TracingCondition tc = (TracingCondition) cond;
            if (result) {
              traceWakeup.record(tc.owner.getNextEventNumber());
            } else {
              traceTimeout.record(tc.owner.getNextEventNumber());
            }
            tc.owner.replayIncrementEventNo();
          }
          return result;

        } catch (InterruptedException e) {
          if (VmSettings.ACTOR_TRACING) {
            traceTimeout.record(0);
          }
          return false;
        }
      } finally {
        ObjectTransitionSafepoint.INSTANCE.register();
      }
    }
  }
}
