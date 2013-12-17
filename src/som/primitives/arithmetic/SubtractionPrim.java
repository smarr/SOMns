package som.primitives.arithmetic;

import java.math.BigInteger;

import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class SubtractionPrim extends ArithmeticPrim {
  public SubtractionPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public SubtractionPrim(final SubtractionPrim node) { this(node.selector, node.universe); }

  @Specialization(order = 1)
  public SAbstractObject doInteger(final int left, final int right) {
    long result = ((long) left) - right;
    return makeInt(result);
  }

  @Specialization(order = 20)
  public SAbstractObject doBigInteger(final BigInteger left, final BigInteger right) {
    BigInteger result = left.subtract(right);
    if (result.bitLength() > 31) {
      return universe.newBigInteger(result);
    } else {
      return universe.newInteger(result.intValue());
    }
  }

  @Specialization(order = 30)
  public SAbstractObject doDouble(final double left, final double right) {
    return universe.newDouble(left - right);
  }

  @Specialization(order = 100)
  public SAbstractObject doInteger(final int left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization(order = 110)
  public SAbstractObject doInteger(final int left, final double right) {
    return doDouble(left, right);
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
