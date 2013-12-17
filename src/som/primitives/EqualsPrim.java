package som.primitives;

import java.math.BigInteger;

import som.interpreter.nodes.BinaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class EqualsPrim extends BinaryMessageNode {
  public EqualsPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public EqualsPrim(final EqualsPrim prim) { this(prim.selector, prim.universe); }

  @Specialization(order = 1)
  public SObject doInteger(final int left, final int right) {
    if (left == right) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 20)
  public SObject doBigInteger(final BigInteger left, final BigInteger right) {
    if (left.compareTo(right) == 0) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 30)
  public SObject doString(final String receiver, final String argument) {
    if (receiver.equals(argument)) {
      return universe.trueObject;
    }
    return universe.falseObject;
  }

  @Specialization(order = 40)
  public SObject doDouble(final double left, final double right) {
    if (left == right) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 50)
  public SObject doSObject(final SObject left, final SObject right) {
    if (left == right) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 100)
  public SObject doInteger(final int left, final double right) {
    if (left == right) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 1000)
  public SObject doBigInteger(final BigInteger left, final int right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 1010)
  public SObject doInteger(final int left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization(order = 1020)
  public SObject doDouble(final double left, final int right) {
    return doDouble(left, (double) right);
  }

  @Specialization(order = 10000)
  public SObject doInteger(final int left, final String right) {
    return universe.falseObject;
  }

  @Specialization(order = 10010)
  public SObject doInteger(final int left, final SObject right) {
    return universe.falseObject;
  }

  @Specialization(order = 10100)
  public SObject doString(final String receiver, final int argument) {
    return universe.falseObject;
  }

  @Specialization(order = 10110)
  public SObject doString(final String receiver, final SObject argument) {
    return universe.falseObject;
  }
}
