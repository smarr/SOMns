package som.primitives.arithmetic;

import java.math.BigInteger;

import som.vm.Universe;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class LogicAndPrim extends ArithmeticPrim {
  public LogicAndPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public LogicAndPrim(final LogicAndPrim prim) { this(prim.selector, prim.universe); }

  @Specialization(order = 1)
  public Object doInteger(final int left, final int right) {
    long result = ((long) left) & right;
    return intOrBigInt(result);
  }

  @Specialization(order = 2)
  public BigInteger doBigInteger(final BigInteger left, final BigInteger right) {
    return left.and(right);
  }

  @Specialization(order = 3)
  public double doDouble(final double receiver, final double right) {
    long left = (long) receiver;
    long rightLong = (long) right;
    return left & rightLong;
  }

  @Specialization(order = 9)
  public double doDouble(final double receiver, final int right) {
    long left = (long) receiver;
    long rightLong = right;
    return left & rightLong;
  }

  @Specialization(order = 10)
  public BigInteger doInteger(final int left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization(order = 11)
  public BigInteger doBigInteger(final BigInteger left, final int right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 12)
  public double doInteger(final int left, final double right) {
    return doDouble(left, right);
  }
}
