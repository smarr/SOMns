package som.primitives.arithmetic;

import java.math.BigInteger;

import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.dsl.Specialization;


public abstract class AdditionPrim extends ArithmeticPrim {
  public AdditionPrim(final boolean executesEnforced) { super(executesEnforced); }
  public AdditionPrim(final AdditionPrim node) { this(node.executesEnforced); }

  @Specialization(order = 10, rewriteOn = ArithmeticException.class)
  public final long doLong(final long left, final long argument) {
    return ExactMath.addExact(left, argument);
  }

  @Specialization(order = 11)
  public final BigInteger doLongWithOverflow(final long left, final long argument) {
    return BigInteger.valueOf(left).add(BigInteger.valueOf(argument));
  }

  @Specialization(order = 30)
  public final Object doBigInteger(final BigInteger left, final BigInteger right) {
    BigInteger result = left.add(right);
    return reduceToIntIfPossible(result);
  }

  @Specialization(order = 40)
  public final double doDouble(final double left, final double right) {
    return right + left;
  }

  @Specialization(order = 50)
  public final String doString(final String left, final String right) {
    return left + right;
  }

  @Specialization(order = 100)
  public final Object doLong(final long left, final BigInteger argument) {
    return doBigInteger(BigInteger.valueOf(left), argument);
  }

  @Specialization(order = 110)
  public final double doLong(final long left, final double argument) {
    return doDouble(left, argument);
  }

  @Specialization(order = 120)
  public final Object doBigInteger(final BigInteger left, final long right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 130)
  public final double doDouble(final double left, final long right) {
    return doDouble(left, (double) right);
  }

  @Specialization(order = 1000)
  public final String doString(final String left, final SClass right) {
    return left + right.getName().getString();
  }

  @Specialization(order = 1001)
  public final String doString(final String left, final SSymbol right) {
    return left + right.getString();
  }
}
