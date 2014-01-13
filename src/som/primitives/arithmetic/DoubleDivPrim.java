package som.primitives.arithmetic;

import java.math.BigInteger;

import som.vm.NotYetImplementedException;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class DoubleDivPrim extends ArithmeticPrim {
  public DoubleDivPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public DoubleDivPrim(final DoubleDivPrim node) { this(node.selector, node.universe); }

  @Specialization(order = 1)
  public double doDouble(final double left, final double right) {
    return left / right;
  }

  @Specialization(order = 2)
  public double doInteger(final int left, final int right) {
    return ((double) left) / right;
  }

  @Specialization(order = 10)
  public double doDouble(final double left, final int right) {
    return doDouble(left, (double) right);
  }

  @Specialization(order = 100)
  public SAbstractObject doInteger(final int left, final BigInteger right) {
    throw new NotYetImplementedException(); // TODO: need to implement the "/" case here directly... : return resendAsBigInteger("/", left, (SBigInteger) rightObj, frame.pack());
  }

  @Specialization(order = 101)
  public SAbstractObject doInteger(final int left, final double right) {
    throw new NotYetImplementedException(); // TODO: need to implement the "/" case here directly... : return resendAsDouble("/", left, (SDouble) rightObj, frame.pack());
  }
}
