package som.primitives.arithmetic;

import java.math.BigInteger;

import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class AdditionPrim extends ArithmeticPrim {
  public AdditionPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public AdditionPrim(final AdditionPrim node) { this(node.selector, node.universe); }

  @Specialization(order = 10)
  public SAbstractObject doInteger(final int left, final int argument) {
    // TODO: handle overflow
    long result = (long) left + argument;
    return makeInt(result);
  }

  @Specialization(order = 30)
  public SAbstractObject doBigInteger(final BigInteger left,
      final BigInteger right) {
    // Do operation and perform conversion to Integer if required
    // TODO: can we optimize this using ExactMath??
    BigInteger result = left.add(right);
    return makeInt(result);
  }

  @Specialization(order = 40)
  public SAbstractObject doDouble(final double left, final double right) {
    return universe.newDouble(right + left);
  }

  @Specialization(order = 100)
  public SAbstractObject doInteger(final int left, final BigInteger argument) {
    return doBigInteger(BigInteger.valueOf(left), argument);
  }

  @Specialization(order = 110)
  public SAbstractObject doInteger(final int left, final double argument) {
    return doDouble(left, argument);
  }

  @Specialization(order = 120)
  public SAbstractObject doBigInteger(final BigInteger left, final int right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 130)
  public SAbstractObject doDouble(final double left, final int right) {
    return doDouble(left, right);
  }
}
