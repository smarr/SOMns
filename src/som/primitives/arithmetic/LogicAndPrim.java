package som.primitives.arithmetic;

import java.math.BigInteger;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class LogicAndPrim extends ArithmeticPrim {
  public LogicAndPrim(final boolean executesEnforced) { super(executesEnforced); }
  public LogicAndPrim(final LogicAndPrim node) { this(node.executesEnforced); }

  @Specialization(order = 1)
  public final long doLong(final long left, final long right) {
    return left & right;
  }

  @Specialization(order = 2)
  public final Object doBigInteger(final BigInteger left, final BigInteger right) {
    return reduceToIntIfPossible(left.and(right));
  }

  @Specialization(order = 10)
  public final Object doLong(final long left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization(order = 11)
  public final Object doBigInteger(final BigInteger left, final long right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }
}
