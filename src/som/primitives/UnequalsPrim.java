package som.primitives;

import java.math.BigInteger;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vm.constants.Globals;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


@GenerateNodeFactory
public abstract class UnequalsPrim extends BinaryExpressionNode {

  @Specialization
  public final boolean doBoolean(final boolean left, final boolean right) {
    return left != right;
  }

  @Specialization
  public final boolean doBoolean(final boolean left, final SObject right) {
    return (left && right != Globals.trueObject) ||
          (!left && right != Globals.falseObject);
  }

  @Specialization
  public final boolean doLong(final long left, final long right) {
    return left != right;
  }

  @Specialization
  public final boolean doBigInteger(final BigInteger left, final BigInteger right) {
    return left.compareTo(right) != 0;
  }

  @Specialization
  public final boolean doString(final String receiver, final String argument) {
    return !receiver.equals(argument);
  }

  @Specialization
  public final boolean doDouble(final double left, final double right) {
    return left != right;
  }

  @Specialization
  public final boolean doSSymbol(final SSymbol left, final SSymbol right) {
    return left != right;
  }

  @Specialization
  public final boolean doLong(final long left, final double right) {
    return left != right;
  }

  @Specialization
  public final boolean doBigInteger(final BigInteger left, final long right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization
  public final boolean doLong(final long left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization
  public final boolean doDouble(final double left, final long right) {
    return doDouble(left, (double) right);
  }

  @Specialization
  public final boolean doLong(final long left, final String right) {
    return true;
  }

  @Specialization
  public final boolean doLong(final long left, final SObject right) {
    return true;
  }

  @Specialization
  public final boolean doLong(final long left, final SSymbol right) {
    return true;
  }

  @Specialization
  public final boolean doString(final String receiver, final long argument) {
    return true;
  }

  @Specialization
  public final boolean doString(final String receiver, final SObject argument) {
    return true;
  }

  @Specialization
  public final boolean doSSymbol(final SSymbol receiver, final long argument) {
    return true;
  }

  @Specialization
  public final boolean doSSymbol(final SSymbol receiver, final SObject argument) {
    return true;
  }
}
