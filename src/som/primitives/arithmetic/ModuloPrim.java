package som.primitives.arithmetic;

import java.math.BigInteger;

import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class ModuloPrim extends ArithmeticPrim {

  public ModuloPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public ModuloPrim(final ModuloPrim node) { this(node.selector, node.universe); }

  @Specialization(order = 1)
  public SAbstractObject doInteger(final int left, final int right) {
    long l = left;
    long r = right;
    long result = l % r;

    if (l > 0 && r < 0) {
      result += r;
    }
    return makeInt(result);
  }

  @Specialization(order = 2)
  public SAbstractObject doBigInteger(final BigInteger left, final BigInteger right) {
    return universe.newBigInteger(left.mod(right));
  }

  @Specialization(order = 3)
  public SAbstractObject doDouble(final double left, final double right) {
    return universe.newDouble(left % right);
  }

  @Specialization(order = 10)
  public SAbstractObject doBigInteger(final BigInteger left, final int right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 11)
  public SAbstractObject doInteger(final int left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization(order = 12)
  public SAbstractObject doInteger(final int left, final double right) {
    return doDouble(left, right);
  }

  @Specialization(order = 13)
  public SAbstractObject doDouble(final double left, final int right) {
    return doDouble(left, right);
  }
}
