package som.primitives.threads;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


public class ConditionPrim {
  @GenerateNodeFactory
  public abstract static class ConditionSignalOnePrim extends UnaryExpressionNode {
    public ConditionSignalOnePrim() { super(null); }
    @Specialization
    public final Condition doThread(final Condition receiver) {
      receiver.signal();
      return receiver;
    }
  }

  @GenerateNodeFactory
  public abstract static class ConditionSignalAllPrim extends UnaryExpressionNode {
    public ConditionSignalAllPrim() { super(null); }
    @Specialization
    public final Condition doThread(final Condition receiver) {
      receiver.signalAll();
      return receiver;
    }
  }

  @GenerateNodeFactory
  public abstract static class ConditionAwaitPrim extends UnaryExpressionNode {
    public ConditionAwaitPrim() { super(null); }
    @Specialization
    public final Condition doThread(final Condition receiver) {
      try {
        receiver.await();
      } catch (InterruptedException e) { /* doesn't tell us a lot at the moment, so is ignored */}
      return receiver;
    }
  }

  @GenerateNodeFactory
  public abstract static class ConditionAwaitForPrim extends BinaryExpressionNode {
    public ConditionAwaitForPrim() { super(null); }
    @Specialization
    public final boolean doThread(final Condition receiver, final long milliseconds) {
      try {
        return receiver.await(milliseconds, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        return false;
      }
    }
  }
}
