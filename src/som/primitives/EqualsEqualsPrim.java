package som.primitives;

import java.math.BigInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import som.interpreter.actors.SFarReference;
import som.primitives.threading.TaskThreads.SomForkJoinTask;
import som.primitives.threading.TaskThreads.SomThreadTask;
import som.vm.constants.Nil;
import som.vmobjects.SArray.SMutableArray;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import som.vmobjects.SSymbol;


@GenerateNodeFactory
@Primitive(primitive = "value:sameAs:")
@Primitive(primitive = "int:equals:")
@Primitive(primitive = "double:equals:")
@Primitive(primitive = "string:equals:")
@Primitive(selector = "=")
@Primitive(selector = "==")
@ImportStatic(Nil.class)
public abstract class EqualsEqualsPrim extends ComparisonPrim {
  @Specialization
  public final boolean doBoolean(final boolean left, final boolean right) {
    return left == right;
  }

  @Specialization
  public final boolean doLong(final long left, final long right) {
    return left == right;
  }

  @Specialization
  @TruffleBoundary
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
  public final boolean doSBlock(final SBlock left, final Object right) {
    return left == right;
  }

  @Specialization(guards = {"left.isValue()", "right.isValue()"})
  public final boolean doValues(final SImmutableObject left, final SImmutableObject right) {
    return left == right;
  }

  @Specialization
  public final boolean doArray(final SMutableArray left, final Object right) {
    return left == right;
  }

  @Specialization
  public final boolean doSMethod(final SInvokable left, final Object right) {
    return left == right;
  }

  @Specialization
  public final boolean doSObject(final SObjectWithClass left, final Object right) {
    return left == right;
  }

  protected static final boolean notFarReference(final Object obj) {
    return !(obj instanceof SFarReference);
  }

  @Specialization(guards = "notFarReference(right)")
  public final boolean doFarRefAndObj(final SFarReference left, final Object right) {
    return false;
  }

  @Specialization
  public final boolean doSSymbol(final SSymbol left, final SSymbol right) {
    return left == right;
  }

  @Specialization
  public final boolean doFarReferences(final SFarReference left, final SFarReference right) {
    // TODO: this is not yet complete, the value compare should be perhaps more than
    // identity
    return left == right ||
        (left.getActor() == right.getActor() && left.getValue() == right.getValue());
  }

  @Specialization
  public final boolean doLong(final long left, final double right) {
    return left == right;
  }

  @Specialization
  @TruffleBoundary
  public final boolean doBigInteger(final BigInteger left, final long right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }

  @Specialization
  @TruffleBoundary
  public final boolean doBigInteger(final BigInteger left, final double right) {
    // TODO: this needs to be properly specified, I don't really know what's
    // the most useful semantics, but this comes 'close', I hope
    return doBigInteger(left, BigInteger.valueOf((long) right));
  }

  @Specialization
  @TruffleBoundary
  public final boolean doDouble(final double left, final BigInteger right) {
    // TODO: this needs to be properly specified, I don't really know what's
    // the most useful semantics, but this comes 'close', I hope
    return doBigInteger(BigInteger.valueOf((long) left), right);
  }

  @Specialization
  @TruffleBoundary
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
  public final boolean doLong(final long left, final SObjectWithClass right) {
    return false;
  }

  @Specialization
  public final boolean doLong(final long left, final SSymbol right) {
    return false;
  }

  @Specialization
  public final boolean doLong(final long left, final boolean right) {
    return false;
  }

  @Specialization
  public final boolean doBigInteger(final BigInteger left, final String right) {
    return false;
  }

  @Specialization
  public final boolean doBigInteger(final BigInteger left, final SSymbol right) {
    return false;
  }

  @Specialization
  public final boolean doDouble(final double left, final boolean right) {
    return false;
  }

  @Specialization
  public final boolean doDouble(final double left, final String right) {
    return false;
  }

  @Specialization
  public final boolean doDouble(final double left, final SSymbol right) {
    return false;
  }

  @Specialization(guards = "valueIsNil(right)")
  public final boolean doDouble(final double left, final SObjectWithoutFields right) {
    return false;
  }

  @Specialization
  public final boolean doBoolean(final boolean left, final long right) {
    return false;
  }

  @Specialization
  public final boolean doBoolean(final boolean left, final BigInteger right) {
    return false;
  }

  @Specialization
  public final boolean doBoolean(final boolean left, final double right) {
    return false;
  }

  @Specialization
  public final boolean doBoolean(final boolean left, final String right) {
    return false;
  }

  @Specialization
  public final boolean doBoolean(final boolean left, final SSymbol right) {
    return false;
  }

  @Specialization
  public final boolean doBigInteger(final BigInteger left, final boolean right) {
    return false;
  }

  @Specialization(guards = "valueIsNil(right)")
  public final boolean doBigInteger(final BigInteger left, final SObjectWithoutFields right) {
    return false;
  }

  @Specialization(guards = "valueIsNil(right)")
  public final boolean doBoolean(final boolean left, final SObjectWithoutFields right) {
    return false;
  }

  @Specialization
  public final boolean doString(final String receiver, final long argument) {
    return false;
  }

  @Specialization
  public final boolean doString(final String receiver, final boolean argument) {
    return false;
  }

  @Specialization
  public final boolean doString(final String receiver, final BigInteger argument) {
    return false;
  }

  @Specialization
  public final boolean doString(final String receiver, final double argument) {
    return false;
  }

  @Specialization
  public final boolean doString(final String receiver, final SObjectWithClass argument) {
    return false;
  }

  @Specialization
  public final boolean doSSymbol(final SSymbol receiver, final long argument) {
    return false;
  }

  @Specialization
  public final boolean doSSymbol(final SSymbol receiver, final boolean argument) {
    return false;
  }

  @Specialization
  public final boolean doSSymbol(final SSymbol receiver, final BigInteger argument) {
    return false;
  }

  @Specialization
  public final boolean doSSymbol(final SSymbol receiver, final double argument) {
    return false;
  }

  @Specialization
  public final boolean doSSymbol(final SSymbol receiver, final SObjectWithClass argument) {
    return false;
  }

  @Specialization
  public final boolean doThread(final SomThreadTask left, final Object right) {
    return left == right;
  }

  @Specialization
  public final boolean doThread(final SomForkJoinTask left, final Object right) {
    return left == right;
  }

  @Specialization
  public final boolean doMutex(final ReentrantLock left, final Object right) {
    return left == right;
  }

  @Specialization
  public final boolean doCondition(final Condition left, final Object right) {
    return left == right;
  }
}
