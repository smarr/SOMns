package som.primitives.arithmetic;

import java.math.BigInteger;

import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBigInteger;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class AdditionPrim extends ArithmeticPrim {
  public AdditionPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public AdditionPrim(final AdditionPrim node) { this(node.selector, node.universe); }

  @Specialization(order = 1)
  public int doInteger(final int left, final int argument) {
    // TODO: handle overflow
    return left + argument;
  }

  @Specialization(order = 2)
  public SAbstractObject doSInteger(final SInteger left, final SInteger argument) {
    long result = ((long) left.getEmbeddedInteger())
        + argument.getEmbeddedInteger();
    return makeInt(result); // TODO!!: optimize using truffle overflow handling!!!
  }

  @Specialization(order = 3)
  public SAbstractObject doSBigInteger(final SBigInteger left,
      final SBigInteger right) {
    // Do operation and perform conversion to Integer if required
    // TODO: can we optimize this using ExactMath??
    BigInteger result = left.getEmbeddedBiginteger().add(
        right.getEmbeddedBiginteger());
    return makeInt(result);
  }

  @Specialization(order = 4)
  public SAbstractObject doSDouble(final SDouble left, final SDouble right) {
    return universe.newDouble(right.getEmbeddedDouble()
        + left.getEmbeddedDouble());
  }

  @Specialization(order = 10)
  public SAbstractObject doSInteger(final SInteger left, final SBigInteger argument) {
    return doSBigInteger(toSBigInteger(left), argument);
  }

  @Specialization(order = 11)
  public SAbstractObject doSInteger(final SInteger left, final SDouble argument) {
    return doSDouble(toSDouble(left), argument);
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
