package som.primitives;

import java.math.BigInteger;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.UnaryBasicOperation;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.arithmetic.ArithmeticPrim;
import som.vm.constants.Classes;
import som.vmobjects.SArray.SMutableArray;
import som.vmobjects.SSymbol;
import tools.dym.Tags.ComplexPrimitiveOperation;
import tools.dym.Tags.OpArithmetic;
import tools.dym.Tags.StringAccess;


public abstract class IntegerPrims {

  @GenerateNodeFactory
  @Primitive(primitive = "intAs32BitSignedValue:",
             selector = "as32BitSignedValue", receiverType = Long.class)
  public abstract static class As32BitSignedValue extends UnaryBasicOperation {
    public As32BitSignedValue(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == OpArithmetic.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }

    @Specialization
    public final long doLong(final long receiver) {
      return (int) receiver;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "intAs32BitUnsignedValue:",
             selector = "as32BitUnsignedValue", receiverType = Long.class)
  public abstract static class As32BitUnsignedValue extends UnaryBasicOperation {
    public As32BitUnsignedValue(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == OpArithmetic.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }

    @Specialization
    public final long doLong(final long receiver) {
      return Integer.toUnsignedLong((int) receiver);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "intFromString:")
  public abstract static class FromStringPrim extends UnaryExpressionNode {
    public FromStringPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == ComplexPrimitiveOperation.class) {
        return true;
      } else if (tag == StringAccess.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }

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
  @Primitive(primitive = "int:leftShift:", selector = "<<", receiverType = Long.class)
  public abstract static class LeftShiftPrim extends ArithmeticPrim {
    protected LeftShiftPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

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
    @TruffleBoundary
    public final Number doLongWithOverflow(final long receiver, final long right) {
      assert right >= 0;  // currently not defined for negative values of right
      assert right <= Integer.MAX_VALUE;

      return reduceToLongIfPossible(
          BigInteger.valueOf(receiver).shiftLeft((int) right));
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "int:unsignedRightShift:", selector = ">>>",
             receiverType = Long.class)
  public abstract static class UnsignedRightShiftPrim extends ArithmeticPrim {
    protected UnsignedRightShiftPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization
    public final long doLong(final long receiver, final long right) {
      return receiver >>> right;
    }
  }

  @GenerateNodeFactory
  @Primitive(selector = "max:", receiverType = Long.class, disabled = true)
  public abstract static class MaxIntPrim extends ArithmeticPrim {
    protected MaxIntPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization
    public final long doLong(final long receiver, final long right) {
      return Math.max(receiver, right);
    }
  }

  @GenerateNodeFactory
  @Primitive(selector = "to:", receiverType = Long.class, disabled = true)
  public abstract static class ToPrim extends BinaryComplexOperation {
    protected ToPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

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

  @GenerateNodeFactory
  @Primitive(selector = "abs", receiverType = Long.class)
  public abstract static class AbsPrim extends UnaryBasicOperation {
    public AbsPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == OpArithmetic.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }

    @Specialization
    public final long doLong(final long receiver) {
      return Math.abs(receiver);
    }
  }
}
