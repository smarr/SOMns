package som.primitives;

import java.math.BigInteger;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vmobjects.SObjectWithoutFields;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


@GenerateNodeFactory
@Primitive({"value:sameAs:", "int:equals:", "double:equals:"})
public abstract class EqualsPrim extends BinaryExpressionNode {
  @Specialization
  public final boolean doBoolean(final boolean left, final boolean right) {
    return left == right;
  }

  @Specialization
  public final boolean doLong(final long left, final long right) {
    return left == right;
  }

  @Specialization
  public final boolean doBigInteger(final BigInteger left, final BigInteger right) {
    return left.compareTo(right) == 0;
  }

  @Specialization
  public final boolean doString(final String receiver, final String argument) {
    return receiver.equals(argument);
  }

  @Specialization
  public final boolean doDouble(final double left, final double right) {
    return left == right;
  }

  @Specialization
  public final boolean doSSymbol(final SSymbol left, final SSymbol right) {
    return left == right;
  }

  @Specialization
  public final boolean doLong(final long left, final double right) {
    return left == right;
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
  public final boolean doString(final String receiver, final SSymbol argument) {
    return receiver.equals(argument.getString());
  }


  @Specialization
  public final boolean doSSymbol(final SSymbol receiver, final String argument) {
    return receiver.getString().equals(argument);
  }


  @Specialization
  public final boolean doLong(final long left, final String right) {
    return false;
  }

  @Specialization
  public final boolean doLong(final long left, final SObjectWithoutFields right) {
    return false;
  }

  @Specialization
  public final boolean doLong(final long left, final SSymbol right) {
    return false;
  }

  @Specialization
  public final boolean doString(final String receiver, final long argument) {
    return false;
  }

  @Specialization
  public final boolean doString(final String receiver, final SObjectWithoutFields argument) {
    return false;
  }

  @Specialization
  public final boolean doSSymbol(final SSymbol receiver, final long argument) {
    return false;
  }

  @Specialization
  public final boolean doSSymbol(final SSymbol receiver, final SObjectWithoutFields argument) {
    return false;
  }
}
