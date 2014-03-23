package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.arithmetic.ArithmeticPrim;
import som.vm.Universe;
import som.vmobjects.SClass;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class IntegerPrims {

  public abstract static class RandomPrim extends UnaryExpressionNode {
    @Specialization
    public final int doInteger(final int receiver) {
      return (int) (receiver * Math.random());
    }

    @Override
    public final void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }
  }

  public abstract static class FromStringPrim extends ArithmeticPrim {
    private final Universe universe;
    public FromStringPrim() { this.universe = Universe.current(); }

    protected final boolean receiverIsIntegerClass(final SClass receiver) {
      return receiver == universe.integerClass;
    }

    @Specialization(guards = "receiverIsIntegerClass")
    public final Object doSClass(final SClass receiver, final String argument) {
      long result = Long.parseLong(argument);
      return intOrBigInt(result);
    }
  }
}
