package som.primitives;

import som.interpreter.nodes.UnaryMessageNode;
import som.primitives.arithmetic.ArithmeticPrim;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class IntegerPrims {


  public abstract static class RandomPrim extends UnaryMessageNode {
    public RandomPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public RandomPrim(final RandomPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public int doInteger(final int receiver) {
      return (int) (receiver * Math.random());
    }
  }

  public abstract static class FromStringPrim extends ArithmeticPrim {
    public FromStringPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public FromStringPrim(final FromStringPrim prim) { this(prim.selector, prim.universe); }

    protected boolean receiverIsIntegerClass(final SClass receiver) {
      return receiver == universe.integerClass;
    }

    @Specialization(guards = "receiverIsIntegerClass")
    public Object doSClass(final SClass receiver, final String argument) {
      long result = Long.parseLong(argument);
      return intOrBigInt(result);
    }
  }
}
