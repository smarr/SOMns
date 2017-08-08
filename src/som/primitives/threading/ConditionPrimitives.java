package som.primitives.threading;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;


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
    @Specialization
    @TruffleBoundary
    public final Condition doCondition(final Condition cond) {
      try {
        cond.await();
      } catch (InterruptedException e) {
        /* doesn't tell us a lot at the moment, so it is ignored */
      }
      return cond;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingAwait:for:")
  public abstract static class AwaitForPrim extends BinaryExpressionNode {
    @Specialization
    @TruffleBoundary
    public final boolean doCondition(final Condition cond, final long milliseconds) {
      try {
        return cond.await(milliseconds, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        return false;
      }
    }
  }
}
