package som.primitives.arithmetic;

import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBigInteger;
import som.vmobjects.SClass;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class LessThanPrim extends ArithmeticPrim {
  public LessThanPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
  public LessThanPrim(final LessThanPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }


  @Specialization(order = 1)
  public SAbstractObject doSInteger(final SInteger left, final SInteger right) {
    if (left.getEmbeddedInteger() < right.getEmbeddedInteger()) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 2)
  public SAbstractObject doSBigInteger(final SBigInteger left, final SBigInteger right) {
    if (left.getEmbeddedBiginteger().compareTo(right.getEmbeddedBiginteger()) < 0) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 3)
  public SAbstractObject doSDouble(final SDouble left, final SDouble right) {
    if (left.getEmbeddedDouble() < right.getEmbeddedDouble()) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
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
