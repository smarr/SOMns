package som.primitives.arithmetic;

import java.math.BigInteger;

import som.vm.Universe;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.dsl.Specialization;


public abstract class SubtractionPrim extends ArithmeticPrim {
  public SubtractionPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public SubtractionPrim(final SubtractionPrim node) { this(node.selector, node.universe); }

  @Specialization(order = 1, rewriteOn = ArithmeticException.class)
  public int doInteger(final int left, final int right) {
    return ExactMath.subtractExact(left, right);
  }

  @Specialization(order = 20)
  public Object doBigInteger(final BigInteger left, final BigInteger right) {
    BigInteger result = left.subtract(right);
    return reduceToIntIfPossible(result);
  }

  @Specialization(order = 30)
  public double doDouble(final double left, final double right) {
    return left - right;
  }

  @Specialization(order = 100)
  public Object doInteger(final int left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization(order = 110)
  public double doInteger(final int left, final double right) {
    return doDouble(left, right);
  }

  @Specialization(order = 120)
  public Object doBigInteger(final BigInteger left, final int right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 130)
  public double doDouble(final double left, final int right) {
    return doDouble(left, right);
  }
}
