package som.primitives.arithmetic;

import java.math.BigInteger;

import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class LogicAndPrim extends ArithmeticPrim {
  public LogicAndPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public LogicAndPrim(final LogicAndPrim prim) { this(prim.selector, prim.universe); }

  @Specialization(order = 1)
  public SAbstractObject doInteger(final int left, final int right) {
    long result = ((long) left) & right;
    return makeInt(result);
  }

  @Specialization(order = 2)
  public SAbstractObject doBigInteger(final BigInteger left, final BigInteger right) {
    return universe.newBigInteger(left.and(right));
  }

  @Specialization(order = 3)
  public SAbstractObject doDouble(final double receiver, final double right) {
    long left = (long) receiver;
    long rightLong = (long) right;
    return universe.newDouble(left & rightLong);
  }

  @Specialization(order = 9)
  public SAbstractObject doDouble(final double receiver, final int right) {
    long left = (long) receiver;
    long rightLong = right;
    return universe.newDouble(left & rightLong);
  }

  @Specialization(order = 10)
  public SAbstractObject doInteger(final int left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization(order = 11)
  public SAbstractObject doBigInteger(final BigInteger left, final int right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 12)
  public SAbstractObject doInteger(final int left, final double right) {
    return doDouble(left, right);
  }
}
