package som.primitives.arithmetic;

import java.math.BigInteger;

import som.vm.Universe;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class LessThanPrim extends ArithmeticPrim {
  private final Universe universe;
  public LessThanPrim() { this.universe = Universe.current(); }


  @Specialization(order = 1)
  public final SObject doInteger(final int left, final int right) {
    if (left < right) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 20)
  public final SObject doBigInteger(final BigInteger left, final BigInteger right) {
    if (left.compareTo(right) < 0) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 30)
  public final SObject doDouble(final double left, final double right) {
    if (left < right) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 100)
  public final SObject doInteger(final int left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization(order = 110)
  public final SObject doInteger(final int left, final double right) {
    return doDouble(left, right);
  }

  @Specialization(order = 120)
  public final SObject doBigInteger(final BigInteger left, final int right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 130)
  public final SObject doDouble(final double left, final int right) {
    return doDouble(left, right);
  }
}
