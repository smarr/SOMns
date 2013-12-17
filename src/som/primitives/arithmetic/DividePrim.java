package som.primitives.arithmetic;

import java.math.BigInteger;

import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class DividePrim extends ArithmeticPrim {
  public DividePrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public DividePrim(final DividePrim node) { this(node.selector, node.universe); }

  @Specialization(order = 1)
  public SAbstractObject doInteger(final int left, final int right) {
    long result = ((long) left) / right;
    return makeInt(result);
  }

  @Specialization(order = 2)
  public SAbstractObject doBigInteger(final BigInteger left, final BigInteger right) {
    // TODO: optimize with ExactMath...
    // Do operation and perform conversion to Integer if required
    BigInteger result = left.divide(right);
    return makeInt(result);
  }

  @Specialization(order = 10)
  public SAbstractObject doBigInteger(final BigInteger left, final int right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 11)
  public SAbstractObject doInteger(final int left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization(order = 13)
  public SAbstractObject doInteger(final int left, final double right) {
    throw new NotImplementedException(); // TODO: need to implement the "//" case here directly... : resendAsDouble("//", left, (SDouble) rightObj, frame.pack());
  }
}
