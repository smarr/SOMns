package som.primitives;

import som.interpreter.nodes.BinaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBigInteger;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SObject;
import som.vmobjects.SString;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class EqualsPrim extends BinaryMessageNode {
  public EqualsPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public EqualsPrim(final EqualsPrim prim) { this(prim.selector, prim.universe); }

  @Specialization(order = 1)
  public SObject doSInteger(final SInteger left, final SInteger right) {
    if (left.getEmbeddedInteger() == right.getEmbeddedInteger()) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 2)
  public SObject doSBigInteger(final SBigInteger left, final SBigInteger right) {
    if (left.getEmbeddedBiginteger().compareTo(
        right.getEmbeddedBiginteger()) == 0) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 3)
  public SObject doSString(final SString receiver, final SString argument) {
    if (receiver.getEmbeddedString().equals(argument.getEmbeddedString())) {
      return universe.trueObject;
    }
    return universe.falseObject;
  }

  @Specialization(order = 4)
  public SObject doSDouble(final SDouble left, final SDouble right) {
    if (left.getEmbeddedDouble() == right.getEmbeddedDouble()) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 10)
  public SObject doSInteger(final SInteger left, final SDouble right) {
    if (left.getEmbeddedInteger() == right.getEmbeddedDouble()) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 100)
  public SObject doSBigInteger(final SBigInteger left, final SInteger right) {
    return doSBigInteger(left, universe.newBigInteger(right.getEmbeddedInteger()));
  }

  @Specialization(order = 101)
  public SObject doSInteger(final SInteger left, final SBigInteger right) {
    SBigInteger leftBigInteger = universe.newBigInteger(left.getEmbeddedInteger());
    return doSBigInteger(leftBigInteger, right);
  }

  @Specialization(order = 102)
  public SObject doSDouble(final SDouble left, final SInteger right) {
    SDouble rightDouble = universe.newDouble(right.getEmbeddedInteger());
    return doSDouble(left, rightDouble);
  }

  @Specialization(order = 1000)
  public SObject doSAbstractObject(final SAbstractObject left, final SAbstractObject right) {
    if (left == right) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }
}
