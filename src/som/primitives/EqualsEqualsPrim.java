package som.primitives;

import java.math.BigInteger;

import som.interpreter.nodes.nary.BinaryExpressionNode.BinarySideEffectFreeExpressionNode;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class EqualsEqualsPrim extends BinarySideEffectFreeExpressionNode {

  @Specialization(order = 1)
  public final boolean doBoolean(final boolean left, final boolean right) {
    return left == right;
  }

  @Specialization(order = 2)
  public final boolean doInteger(final int left, final int right) {
    return left == right;
  }

  @Specialization(order = 20)
  public final boolean doBigInteger(final BigInteger left, final BigInteger right) {
    return left == right;
  }

  @Specialization(order = 30)
  public final boolean doString(final String left, final String right) {
    return left == right;
  }

  @Specialization(order = 40)
  public final boolean doDouble(final double left, final double right) {
    return left == right;
  }

  @Specialization(order = 45)
  public final boolean doSBlock(final SBlock left, final Object right) {
    return left == right;
  }

  @Specialization(order = 46)
  public final boolean doSArray(final Object[] left, final Object right) {
    return left == right;
  }

  @Specialization(order = 47)
  public final boolean doSMethod(final SInvokable left, final Object right) {
    return left == right;
  }

  @Specialization(order = 48)
  public final boolean doSSymbol(final SSymbol left, final Object right) {
    return left == right;
  }

  @Specialization(order = 50)
  public final boolean doSObject(final SObject left, final Object right) {
    return left == right;
  }

  @Specialization(order = 100)
  public final boolean doInteger(final int left, final double right) {
    return false;
  }

  @Specialization(order = 1000)
  public final boolean doBigInteger(final BigInteger left, final int right) {
    return false;
  }

  @Specialization(order = 1010)
  public final boolean doInteger(final int left, final BigInteger right) {
    return false;
  }

  @Specialization(order = 1020)
  public final boolean doDouble(final double left, final int right) {
    return false;
  }

  @Specialization(order = 10000)
  public final boolean doInteger(final int left, final String right) {
    return false;
  }

  @Specialization(order = 10010)
  public final boolean doInteger(final int left, final SObject right) {
    return false;
  }

  @Specialization(order = 10100)
  public final boolean doString(final String receiver, final int argument) {
    return false;
  }

  @Specialization(order = 10110)
  public final boolean doString(final String receiver, final SObject argument) {
    return false;
  }
}
