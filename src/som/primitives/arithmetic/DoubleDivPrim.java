package som.primitives.arithmetic;

import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBigInteger;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SSymbol;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class DoubleDivPrim extends ArithmeticPrim {
  public DoubleDivPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public DoubleDivPrim(final DoubleDivPrim node) { this(node.selector, node.universe); }

  @Specialization(order = 1)
  public SAbstractObject doSDouble(final SDouble left, final SDouble right) {
    return universe.newDouble(left.getEmbeddedDouble()
        / right.getEmbeddedDouble());
  }

  @Specialization(order = 2)
  public SAbstractObject doSInteger(final SInteger left, final SInteger right) {
    double result = ((double) left.getEmbeddedInteger())
        / right.getEmbeddedInteger();
    return universe.newDouble(result);
  }

  @Specialization(order = 10)
  public SAbstractObject doSDouble(final SDouble left, final SInteger right) {
    return doSDouble(left, toSDouble(right));
  }

  @Specialization(order = 100)
  public SAbstractObject doSInteger(final SInteger left, final SBigInteger right) {
    throw new NotImplementedException(); // TODO: need to implement the "/" case here directly... : return resendAsBigInteger("/", left, (SBigInteger) rightObj, frame.pack());
  }

  @Specialization(order = 101)
  public SAbstractObject doSInteger(final SInteger left, final SDouble right) {
    throw new NotImplementedException(); // TODO: need to implement the "/" case here directly... : return resendAsDouble("/", left, (SDouble) rightObj, frame.pack());
  }
}
