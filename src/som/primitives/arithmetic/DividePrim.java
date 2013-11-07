package som.primitives.arithmetic;

import java.math.BigInteger;

import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBigInteger;
import som.vmobjects.SClass;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class DividePrim extends ArithmeticPrim {
  public DividePrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
  public DividePrim(final DividePrim node) { this(node.selector, node.universe, node.rcvrClass, node.invokable); }

  @Specialization(order = 1)
  public SAbstractObject doSInteger(final SInteger left, final SInteger right) {
    long result = ((long) left.getEmbeddedInteger())
        / right.getEmbeddedInteger();
    return makeInt(result);
  }

  @Specialization(order = 2)
  public SAbstractObject doSBigInteger(final SBigInteger left, final SBigInteger right) {
    // TODO: optimize with ExactMath...
    // Do operation and perform conversion to Integer if required
    BigInteger result = left.getEmbeddedBiginteger().divide(
        right.getEmbeddedBiginteger());
    return makeInt(result);
  }

  @Specialization(order = 10)
  public SAbstractObject doSBigInteger(final SBigInteger left, final SInteger right) {
    return doSBigInteger(left, toSBigInteger(right));
  }

  @Specialization(order = 11)
  public SAbstractObject doSInteger(final SInteger left, final SBigInteger right) {
    return doSBigInteger(toSBigInteger(left), right);
  }

  @Specialization(order = 13)
  public SAbstractObject doSInteger(final SInteger left, final SDouble right) {
    throw new NotImplementedException(); // TODO: need to implement the "//" case here directly... : resendAsDouble("//", left, (SDouble) rightObj, frame.pack());
  }
}
