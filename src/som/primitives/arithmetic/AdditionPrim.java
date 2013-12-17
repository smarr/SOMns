package som.primitives.arithmetic;

import java.math.BigInteger;

import som.vm.Universe;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.dsl.Specialization;


public abstract class AdditionPrim extends ArithmeticPrim {
  public AdditionPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public AdditionPrim(final AdditionPrim node) { this(node.selector, node.universe); }

  @Specialization(order = 10, rewriteOn = ArithmeticException.class)
  public int doInteger(final int left, final int argument) {
    return ExactMath.addExact(left, argument);
  }

  @Specialization(order = 30)
  public Object doBigInteger(final BigInteger left, final BigInteger right) {
    BigInteger result = left.add(right);
    return reduceToIntIfPossible(result);
  }

  @Specialization(order = 40)
  public double doDouble(final double left, final double right) {
    return right + left;
  }

  @Specialization(order = 100)
  public Object doInteger(final int left, final BigInteger argument) {
    return doBigInteger(BigInteger.valueOf(left), argument);
  }

  @Specialization(order = 110)
  public double doInteger(final int left, final double argument) {
    return doDouble(left, argument);
  }

  @Specialization(order = 120)
  public Object doBigInteger(final BigInteger left, final int right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 130)
  public double doDouble(final double left, final int right) {
    return doDouble(left, (double) right);
  }
}
