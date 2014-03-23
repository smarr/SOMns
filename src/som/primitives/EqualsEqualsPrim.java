package som.primitives;

import java.math.BigInteger;

import som.interpreter.nodes.nary.BinaryExpressionNode.BinarySideEffectFreeExpressionNode;
import som.vm.Universe;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class EqualsEqualsPrim extends BinarySideEffectFreeExpressionNode {
  private final Universe universe;
  public EqualsEqualsPrim() { this.universe = Universe.current(); }

  @Specialization(order = 1)
  public final SObject doInteger(final int left, final int right) {
    if (left == right) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 20)
  public final SObject doBigInteger(final BigInteger left, final BigInteger right) {
    if (left == right) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 30)
  public final SObject doString(final String receiver, final String argument) {
    if (receiver == argument) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 40)
  public final SObject doDouble(final double left, final double right) {
    if (left == right) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 45)
  public final SObject doSBlock(final SBlock left, final Object right) {
    if (left == right) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 46)
  public final SObject doSArray(final SArray left, final Object right) {
    if (left == right) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 47)
  public final SObject doSMethod(final SInvokable left, final Object right) {
    if (left == right) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 48)
  public final SObject doSSymbol(final SSymbol left, final Object right) {
    if (left == right) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 50)
  public final SObject doSObject(final SObject left, final Object right) {
    if (left == right) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization(order = 100)
  public final SObject doInteger(final int left, final double right) {
    return universe.falseObject;
  }

  @Specialization(order = 1000)
  public final SObject doBigInteger(final BigInteger left, final int right) {
    return universe.falseObject;
  }

  @Specialization(order = 1010)
  public final SObject doInteger(final int left, final BigInteger right) {
    return universe.falseObject;
  }

  @Specialization(order = 1020)
  public final SObject doDouble(final double left, final int right) {
    return universe.falseObject;
  }

  @Specialization(order = 10000)
  public final SObject doInteger(final int left, final String right) {
    return universe.falseObject;
  }

  @Specialization(order = 10010)
  public final SObject doInteger(final int left, final SObject right) {
    return universe.falseObject;
  }

  @Specialization(order = 10100)
  public final SObject doString(final String receiver, final int argument) {
    return universe.falseObject;
  }

  @Specialization(order = 10110)
  public final SObject doString(final String receiver, final SObject argument) {
    return universe.falseObject;
  }
}
