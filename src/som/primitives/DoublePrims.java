package som.primitives;

import som.interpreter.nodes.UnaryMessageNode;
import som.primitives.arithmetic.ArithmeticPrim;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class DoublePrims  {

  public abstract static class RoundPrim extends UnaryMessageNode {
    public RoundPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public RoundPrim(final RoundPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public SAbstractObject doDouble(final double receiver) {
      long result = Math.round(receiver);
      if (result > Integer.MAX_VALUE || result < Integer.MIN_VALUE) {
        return universe.newBigInteger(result);
      } else {
        return universe.newInteger((int) result);
      }
    }
  }

  public abstract static class BitXorPrim extends ArithmeticPrim {
    public BitXorPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public BitXorPrim(final BitXorPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public SAbstractObject doDouble(final double receiver, final double right) {
      long left = (long) receiver;
      long rightLong = (long) right;
      return universe.newDouble(left ^ rightLong);
    }

    @Specialization
    public SAbstractObject doDouble(final double receiver, final int right) {
      long left = (long) receiver;
      long rightLong = right;
      return universe.newDouble(left ^ rightLong);
    }
  }
}
