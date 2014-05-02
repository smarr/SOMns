package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.primitives.arithmetic.ArithmeticPrim;
import som.vm.Universe;
import som.vmobjects.SClass;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class IntegerPrims {

  public abstract static class RandomPrim extends UnarySideEffectFreeExpressionNode {
    @Specialization
    public final long doLong(final long receiver) {
      return (long) (receiver * Math.random());
    }
  }

  public abstract static class FromStringPrim extends ArithmeticPrim {
    private final Universe universe;
    public FromStringPrim() { this.universe = Universe.current(); }

    protected final boolean receiverIsIntegerClass(final SClass receiver) {
      return receiver == universe.integerClass;
    }

    @Specialization(guards = "receiverIsIntegerClass")
    public final Object doSClass(final SClass receiver, final String argument) {
      return Long.parseLong(argument);
    }
  }

  public abstract static class LeftShiftPrim extends ArithmeticPrim {
    @Specialization
    public final long doLong(final long receiver, final long right) {
      return receiver << right;
    }
  }
}
