package som.primitives.arithmetic;

import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBigInteger;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class LogicAndPrim extends ArithmeticPrim {
  public LogicAndPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public LogicAndPrim(final LogicAndPrim prim) { this(prim.selector, prim.universe); }

  @Specialization(order = 1)
  public SAbstractObject doSInteger(final SInteger left, final SInteger right) {
    long result = ((long) left.getEmbeddedInteger())
        & right.getEmbeddedInteger();
    return makeInt(result);
  }

  @Specialization(order = 2)
  public SAbstractObject doSBigInteger(final SBigInteger left, final SBigInteger right) {
    return universe.newBigInteger(left.getEmbeddedBiginteger().and(
        right.getEmbeddedBiginteger()));
  }

  @Specialization(order = 3)
  public SAbstractObject doSDouble(final SDouble receiver, final SDouble right) {
    long left = (long) receiver.getEmbeddedDouble();
    long rightLong = (long) right.getEmbeddedDouble();
    return universe.newDouble(left & rightLong);
  }

  @Specialization(order = 9)
  public SAbstractObject doSDouble(final SDouble receiver, final SInteger right) {
    long left = (long) receiver.getEmbeddedDouble();
    long rightLong = right.getEmbeddedInteger();
    return universe.newDouble(left & rightLong);
  }

  @Specialization(order = 10)
  public SAbstractObject doSInteger(final SInteger left, final SBigInteger right) {
    return doSBigInteger(toSBigInteger(left), right);
  }

  @Specialization(order = 11)
  public SAbstractObject doSBigInteger(final SBigInteger left, final SInteger right) {
    return doSBigInteger(left, toSBigInteger(right));
  }

  @Specialization(order = 12)
  public SAbstractObject doSInteger(final SInteger left, final SDouble right) {
    return doSDouble(toSDouble(left), right);
  }
}
