package som.primitives;

import java.math.BigInteger;

import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.arithmetic.ArithmeticPrim;
import som.vm.constants.Classes;
import som.vmobjects.SArray.SMutableArray;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;


public abstract class IntegerPrims {

  @GenerateNodeFactory
  @Primitive("intAs32BitSignedValue:")
  public abstract static class As32BitSignedValue extends UnaryExpressionNode {
    @Specialization
    public final long doLong(final long receiver) {
      return (int) receiver;
    }
  }

  @GenerateNodeFactory
  @Primitive("intAs32BitUnsignedValue:")
  public abstract static class As32BitUnsignedValue extends UnaryExpressionNode {
    @Specialization
    public final long doLong(final long receiver) {
      return Integer.toUnsignedLong((int) receiver);
    }
  }

  @GenerateNodeFactory
  @Primitive("intFromString:")
  public abstract static class FromStringPrim extends UnaryExpressionNode {

    @Specialization
    public final Object doSClass(final String argument) {
      return Long.parseLong(argument);
    }

    @Specialization
    public final Object doSClass(final SSymbol argument) {
      return Long.parseLong(argument.getString());
    }
  }

  @GenerateNodeFactory
  @Primitive("int:leftShift:")
  public abstract static class LeftShiftPrim extends ArithmeticPrim {
    protected LeftShiftPrim(final SourceSection source) { super(source); }

    private final BranchProfile overflow = BranchProfile.create();

    @Specialization(rewriteOn = ArithmeticException.class)
    public final long doLong(final long receiver, final long right) {
      assert right >= 0;  // currently not defined for negative values of right

      if (Long.SIZE - Long.numberOfLeadingZeros(receiver) + right > Long.SIZE - 1) {
        overflow.enter();
        throw new ArithmeticException("shift overflows long");
      }
      return receiver << right;
    }

    @Specialization
    public final Number doLongWithOverflow(final long receiver, final long right) {
      assert right >= 0;  // currently not defined for negative values of right
      assert right <= Integer.MAX_VALUE;

      return reduceToLongIfPossible(
          BigInteger.valueOf(receiver).shiftLeft((int) right));
    }
  }

  @GenerateNodeFactory
  @Primitive("int:unsignedRightShift:")
  public abstract static class UnsignedRightShiftPrim extends ArithmeticPrim {
    protected UnsignedRightShiftPrim(final SourceSection source) { super(source); }

    @Specialization
    public final long doLong(final long receiver, final long right) {
      return receiver >>> right;
    }
  }

  public abstract static class MaxIntPrim extends ArithmeticPrim {
    protected MaxIntPrim(final SourceSection source) { super(source); }

    @Specialization
    public final long doLong(final long receiver, final long right) {
      return Math.max(receiver, right);
    }
  }

  public abstract static class ToPrim extends BinaryComplexOperation {

    protected ToPrim(final SourceSection source) {
      super(source);
    }

    @Specialization
    public final SMutableArray doLong(final long receiver, final long right) {
      int cnt = (int) right - (int) receiver + 1;
      long[] arr = new long[cnt];
      for (int i = 0; i < cnt; i++) {
        arr[i] = i + receiver;
      }
      return new SMutableArray(arr, Classes.arrayClass);
    }
  }

  public abstract static class AbsPrim extends UnaryExpressionNode {
    @Specialization
    public final long doLong(final long receiver) {
      return Math.abs(receiver);
    }
  }
}
