package som.primitives.arithmetic;

import java.math.BigInteger;

import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class MultiplicationPrim extends ArithmeticPrim {
  public MultiplicationPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public MultiplicationPrim(final MultiplicationPrim node) { this(node.selector, node.universe); }


  @Specialization(order = 1)
  public SAbstractObject doInteger(final int left, final int right) {
    long result = ((long) left) * right;
    return makeInt(result);
  }

  @Specialization(order = 2)
  public SAbstractObject doBigInteger(final BigInteger left, final BigInteger right) {
    BigInteger result = left.multiply(right);
    return makeInt(result);
  }

  @Specialization(order = 3)
  public SAbstractObject doDouble(final double left, final double right) {
    return universe.newDouble(left * right);
  }

  @Specialization(order = 10)
  public SAbstractObject doInteger(final int left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization(order = 11)
  public SAbstractObject doInteger(final int left, final double right) {
    return doDouble(left, right);
  }

  @Specialization(order = 12)
  public SAbstractObject doBigInteger(final BigInteger left, final int right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 13)
  public SAbstractObject doDouble(final double left, final int right) {
    return doDouble(left, (double) right);
  }
}
