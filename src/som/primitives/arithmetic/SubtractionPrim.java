package som.primitives.arithmetic;

import java.math.BigInteger;

import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBigInteger;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class SubtractionPrim extends ArithmeticPrim {
  public SubtractionPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public SubtractionPrim(final SubtractionPrim node) { this(node.selector, node.universe); }

  @Specialization(order = 1)
  public SAbstractObject doSInteger(final SInteger left, final SInteger right) {
    long result = ((long) left.getEmbeddedInteger()) - right.getEmbeddedInteger();
    return makeInt(result);
  }

  @Specialization(order = 2)
  public SAbstractObject doSBigInteger(final SBigInteger left, final SBigInteger right) {
    BigInteger result = left.getEmbeddedBiginteger().subtract(
        right.getEmbeddedBiginteger());
    if (result.bitLength() > 31) {
      return universe.newBigInteger(result);
    } else {
      return universe.newInteger(result.intValue());
    }
  }

  @Specialization(order = 3)
  public SAbstractObject doSDouble(final SDouble left, final SDouble right) {
    return universe.newDouble(left.getEmbeddedDouble() - right.getEmbeddedDouble());
  }

  @Specialization(order = 10)
  public SAbstractObject doSInteger(final SInteger left, final SBigInteger right) {
    return doSBigInteger(toSBigInteger(left), right);
  }

  @Specialization(order = 11)
  public SAbstractObject doSInteger(final SInteger left, final SDouble right) {
    return doSDouble(toSDouble(left), right);
  }

  @Specialization(order = 12)
  public SAbstractObject doSBigInteger(final SBigInteger left, final SInteger right) {
    return doSBigInteger(left, toSBigInteger(right));
  }

  @Specialization(order = 13)
  public SAbstractObject doSDouble(final SDouble left, final SInteger right) {
    return doSDouble(left, toSDouble(right));
  }
}
