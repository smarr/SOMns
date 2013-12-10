package som.primitives;

import som.interpreter.nodes.UnaryMessageNode;
import som.primitives.arithmetic.ArithmeticPrim;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class DoublePrims  {

  public abstract static class RoundPrim extends UnaryMessageNode {
    public RoundPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public RoundPrim(final RoundPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public SAbstractObject doSDouble(final SDouble receiver) {
      long result = Math.round(receiver.getEmbeddedDouble());
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
    public SAbstractObject doSDouble(final SDouble receiver, final SDouble right) {
      long left = (long) receiver.getEmbeddedDouble();
      long rightLong = (long) right.getEmbeddedDouble();
      return universe.newDouble(left ^ rightLong);
    }

    @Specialization
    public SAbstractObject doSDouble(final SDouble receiver, final SInteger right) {
      long left = (long) receiver.getEmbeddedDouble();
      long rightLong = right.getEmbeddedInteger();
      return universe.newDouble(left ^ rightLong);
    }
  }
}
