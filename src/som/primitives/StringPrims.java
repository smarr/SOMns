package som.primitives;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;

import bd.primitives.Primitive;
import som.VM;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryBasicOperation;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.Symbols;
import som.vm.constants.KernelObj;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray;
import som.vmobjects.SSymbol;
import tools.dym.Tags.ComplexPrimitiveOperation;
import tools.dym.Tags.StringAccess;


public class StringPrims {

  @GenerateNodeFactory
  @Primitive(primitive = "string:concat:")
  public abstract static class ConcatPrim extends BinaryComplexOperation {
    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == StringAccess.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }

    @Specialization
    @TruffleBoundary
    public final String doString(final String receiver, final String argument) {
      return receiver + argument;
    }

    @Specialization
    @TruffleBoundary
    public final String doString(final String receiver, final SSymbol argument) {
      return receiver + argument.getString();
    }

    @Specialization
    @TruffleBoundary
    public final String doSSymbol(final SSymbol receiver, final String argument) {
      return receiver.getString() + argument;
    }

    @Specialization
    @TruffleBoundary
    public final String doSSymbol(final SSymbol receiver, final SSymbol argument) {
      return receiver.getString() + argument.getString();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "stringAsSymbol:")
  public abstract static class AsSymbolPrim extends UnaryBasicOperation {
    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == StringAccess.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }

    @Specialization
    public final SAbstractObject doString(final String receiver) {
      return Symbols.symbolFor(receiver);
    }

    @Specialization
    public final SAbstractObject doSSymbol(final SSymbol receiver) {
      return receiver;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "string:substringFrom:to:",
      selector = "substringFrom:to:", receiverType = String.class)
  public abstract static class SubstringPrim extends TernaryExpressionNode {
    private final BranchProfile invalidArgs = BranchProfile.create();

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == StringAccess.class) {
        return true;
      } else if (tag == ComplexPrimitiveOperation.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }

    @Specialization
    public final String doString(final String receiver, final long start,
        final long endIndex) {
      int beginIndex = (int) start - 1;
      int end = (int) endIndex;
      if (beginIndex < 0) {
        invalidArgs.enter();
        return "Error - index out of bounds";
      }

      if (end > receiver.length()) {
        invalidArgs.enter();
        return "Error - index out of bounds";
      }

      int subLen = end - beginIndex;
      if (subLen < 0) {
        invalidArgs.enter();
        return "Error - index out of bounds";
      }

      return receiver.substring(beginIndex, end);
    }

    @Specialization
    public final String doSSymbol(final SSymbol receiver, final long start,
        final long end) {
      return doString(receiver.getString(), start, end);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "string:charAt:", selector = "charAt:", receiverType = String.class)
  public abstract static class CharAtPrim extends BinaryExpressionNode {
    private final BranchProfile invalidArgs = BranchProfile.create();

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == StringAccess.class) {
        return true;
      } else if (tag == ComplexPrimitiveOperation.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }

    @Specialization
    public final String doString(final String receiver, final long idx) {
      int i = (int) idx - 1;
      if (i < 0 || i >= receiver.length()) {
        invalidArgs.enter();
        return "Error - index out of bounds";
      }
      return String.valueOf(receiver.charAt(i));
    }

    @Specialization
    public final String doSSymbol(final SSymbol sym, final long idx) {
      return doString(sym.getString(), idx);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "stringFromArray:")
  public abstract static class FromArrayPrim extends UnaryExpressionNode {

    @Specialization
    public final String doString(final SArray chars) {
      VM.thisMethodNeedsToBeOptimized(
          "Method not yet optimal for compilation, should speculate or use branch profile in the loop");
      Object[] storage = chars.getObjectStorage(SArray.ObjectStorageType);
      StringBuilder sb = new StringBuilder(storage.length);
      for (Object o : storage) {
        if (o instanceof String) {
          sb.append((String) o);
        } else if (o instanceof SSymbol) {
          sb.append(((SSymbol) o).getString());
        } else {
          // TODO: there should be a Smalltalk asString message here, I think
          KernelObj.signalException("signalArgumentError:",
              "Array can't contain non-string objects, but has " + o.toString());
        }
      }

      return sb.toString();
    }

    @Fallback
    public final void doGeneric(final Object obj) {
      KernelObj.signalException("signalInvalidArgument:", obj);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "stringFromCodepoint:")
  public abstract static class FromCodepointPrim extends UnaryExpressionNode {

    protected static final boolean isStrictlyBmpCodePoint(final long val) {
      // SM: Based on Character.isBmpCodePoint(val)
      return val >>> 16 == 0;
    }

    @Specialization(guards = "isStrictlyBmpCodePoint(val)")
    public final String doString(final long val) {
      return new String(new char[] {(char) val});
    }

    protected static final boolean isValidCodePointButNotBmp(final long val) {
      // SM: based on Character.isValidCodePoint(val);
      long plane = val >>> 16;
      if (plane == 0) {
        // Only the case for BMP, which is separate specialization
        return false;
      }
      return plane < ((Character.MAX_CODE_POINT + 1) >>> 16);
    }

    private static void toSurrogates(final int codePoint, final char[] dst, final int index) {
      // SM: Copy from Character.toSurrogates(.)
      // We write elements "backwards" to guarantee all-or-nothing
      dst[index + 1] = Character.lowSurrogate(codePoint);
      dst[index] = Character.highSurrogate(codePoint);
    }

    @Specialization(guards = "isValidCodePointButNotBmp(val)")
    public final String doUnicodeChar(final long val) {
      char[] result = new char[2];
      toSurrogates((int) val, result, 0);
      return new String(result);
    }

    @Fallback
    public final void doGeneric(final Object val) {
      KernelObj.signalException("signalArgumentError:",
          "The value " + val + " is not a valid Unicode code point.");
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "string:codepointAt:")
  public abstract static class CodepointAtPrim extends BinaryExpressionNode {

    @Specialization
    public final long doString(final String str, final long idx) {
      VM.thisMethodNeedsToBeOptimized(
          "CodepointAtPrim: we probably want to specialize here to ideally only have a char read and check");
      int i = (int) idx - 1; // go from 1-based to 0-based
      return str.codePointAt(i);
    }
  }
}
