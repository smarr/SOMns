package som.primitives.arithmetic;

import java.math.BigInteger;

import som.vm.Universe;
import som.vmobjects.SSymbol;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class DividePrim extends ArithmeticPrim {
  public DividePrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public DividePrim(final DividePrim node) { this(node.selector, node.universe); }

  @Specialization(order = 1)
  public Object doInteger(final int left, final int right) {
    long result = ((long) left) / right;
    return intOrBigInt(result);
  }

  @Specialization(order = 2)
  public Object doBigInteger(final BigInteger left, final BigInteger right) {
    BigInteger result = left.divide(right);
    return reduceToIntIfPossible(result);
  }

  @Specialization(order = 10)
  public Object doBigInteger(final BigInteger left, final int right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 11)
  public Object doInteger(final int left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization(order = 13)
  public Object doInteger(final int left, final double right) {
    throw new NotImplementedException(); // TODO: need to implement the "//" case here directly... : resendAsDouble("//", left, (SDouble) rightObj, frame.pack());
  }
}
