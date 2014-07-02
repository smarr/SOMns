package som.primitives;

import java.math.BigInteger;

import som.interpreter.nodes.nary.BinaryExpressionNode.BinarySideEffectFreeExpressionNode;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class EqualsPrim extends BinarySideEffectFreeExpressionNode {
  public EqualsPrim(final boolean executesEnforced) { super(executesEnforced); }
  public EqualsPrim(final EqualsPrim node) { super(node.executesEnforced); }

  @Specialization(order = 1)
  public final boolean doBoolean(final boolean left, final boolean right) {
    return left == right;
  }

  @Specialization(order = 2)
  public final boolean doLong(final long left, final long right) {
    return left == right;
  }

  @Specialization(order = 20)
  public final boolean doBigInteger(final BigInteger left, final BigInteger right) {
    return left.compareTo(right) == 0;
  }

  @Specialization(order = 30)
  public final boolean doString(final String receiver, final String argument) {
    return receiver.equals(argument);
  }

  @Specialization(order = 40)
  public final boolean doDouble(final double left, final double right) {
    return left == right;
  }

  @Specialization(order = 60)
  public final boolean doSSymbol(final SSymbol left, final SSymbol right) {
    return left == right;
  }

  @Specialization(order = 100)
  public final boolean doLong(final long left, final double right) {
    return left == right;
  }

  @Specialization(order = 1000)
  public final boolean doBigInteger(final BigInteger left, final long right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization(order = 1010)
  public final boolean doLong(final long left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization(order = 1020)
  public final boolean doDouble(final double left, final long right) {
    return doDouble(left, (double) right);
  }

  @Specialization(order = 10000)
  public final boolean doLong(final long left, final String right) {
    return false;
  }

  @Specialization(order = 10010)
  public final boolean doLong(final long left, final SObject right) {
    return false;
  }

  @Specialization(order = 10100)
  public final boolean doString(final String receiver, final long argument) {
    return false;
  }

  @Specialization(order = 10110)
  public final boolean doString(final String receiver, final SObject argument) {
    return false;
  }

  @Specialization(order = 10101)
  public final boolean doSSymbol(final SSymbol receiver, final long argument) {
    return false;
  }

  @Specialization(order = 10102)
  public final boolean doSSymbol(final SSymbol receiver, final SObject argument) {
    return false;
  }
}
