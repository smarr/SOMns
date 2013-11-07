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

public abstract class ModuloPrim extends ArithmeticPrim {

  public ModuloPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
  public ModuloPrim(final ModuloPrim node) { this(node.selector, node.universe, node.rcvrClass, node.invokable); }

  @Specialization(order = 1)
  public SAbstractObject doSInteger(final SInteger left, final SInteger right) {
    long l = left.getEmbeddedInteger();
    long r = right.getEmbeddedInteger();
    long result = l % r;

    if (l > 0 && r < 0) {
      result += r;
    }
    return makeInt(result);
  }

  @Specialization(order = 2)
  public SAbstractObject doSBigInteger(final SBigInteger left, final SBigInteger right) {
    return universe.newBigInteger(left.getEmbeddedBiginteger().mod(
        right.getEmbeddedBiginteger()));
  }

  @Specialization(order = 3)
  public SAbstractObject doSDouble(final SDouble left, final SDouble right) {
    return universe.newDouble(left.getEmbeddedDouble()
        % right.getEmbeddedDouble());
  }

  @Specialization(order = 10)
  public SAbstractObject doSBigInteger(final SBigInteger left, final SInteger right) {
    return doSBigInteger(left, toSBigInteger(right));
  }

  @Specialization(order = 11)
  public SAbstractObject doSInteger(final SInteger left, final SBigInteger right) {
    return doSBigInteger(toSBigInteger(left), right);
  }

  @Specialization(order = 12)
  public SAbstractObject doSInteger(final SInteger left, final SDouble right) {
    return doSDouble(toSDouble(left), right);
  }

  @Specialization(order = 13)
  public SAbstractObject doSDouble(final SDouble left, final SInteger right) {
    return doSDouble(left, toSDouble(right));
  }
}
