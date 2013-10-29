package som.primitives.arithmetic;

import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBigInteger;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class SqrtPrim extends ArithmeticPrim {

  public SqrtPrim(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  public SqrtPrim(final SqrtPrim node) {
    this(node.selector, node.universe);
  }

  @Specialization
  public SAbstractObject doSInteger(final VirtualFrame frame,
      final SInteger left, final Object arguments) {
    double result = Math.sqrt(left.getEmbeddedInteger());

    if (result == Math.rint(result)) {
      return makeInt((long) result);
    } else {
      return universe.newDouble(result);
    }
  }

  @Specialization
  public SAbstractObject doSBigInteger(final VirtualFrame frame,
      final SBigInteger left, final Object arguments) {
    return universe.newDouble(Math.sqrt(
        left.getEmbeddedBiginteger().doubleValue()));
  }

  @Specialization
  public SAbstractObject doSDouble(final VirtualFrame frame,
      final SDouble left, final Object arguments) {
    return universe.newDouble(Math.sqrt(left.getEmbeddedDouble()));
  }
}
