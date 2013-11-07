package som.primitives;

import som.interpreter.nodes.messages.UnaryMonomorphicNode;
import som.primitives.arithmetic.ArithmeticPrim;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class DoublePrims  {

  public abstract static class RoundPrim extends UnaryMonomorphicNode {
    public RoundPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public RoundPrim(final RoundPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization
    public SAbstractObject doSDouble(final SDouble receiver) {
      long result = Math.round(receiver.getEmbeddedDouble());
      if (result > java.lang.Integer.MAX_VALUE
          || result < java.lang.Integer.MIN_VALUE) {
        return universe.newBigInteger(result);
      } else {
        return universe.newInteger((int) result);
      }
    }
  }

  public abstract static class BitXorPrim extends ArithmeticPrim {
    public BitXorPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public BitXorPrim(final BitXorPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

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
