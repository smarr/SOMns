package som.primitives;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;

import bd.primitives.Primitive;
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
  }

  @GenerateNodeFactory
  @Primitive(primitive = "stringFrom:")
  public abstract static class StringFromPrim extends UnaryExpressionNode {

    @Specialization
    public final String doString(final SArray chars) {
      Object[] storage = chars.getObjectStorage(chars.ObjectStorageType);
      StringBuilder sb = new StringBuilder(storage.length);
      for (Object o : storage) {
        if (!(o instanceof String) || ((String) o).length() != 1) {
          KernelObj.signalException("signalArgumentError:", storage);
        }
        sb.append((String) o);
      }

      return sb.toString();
    }

    @Specialization
    public final void doGeneric(final Object obj) {
      KernelObj.signalException("signalInvalidArgument:", obj);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "charFrom:")
  public abstract static class CharFromPrim extends UnaryExpressionNode {

    public boolean inRange(final long val) {
      return (0 < val && val < 128);
    }

    @Specialization(guards = "inRange(val)")
    public final String doString(final long val) {
      return new String(new char[] {(char) val});
    }

    @Specialization
    public final void doGeneric(final long val) {
      KernelObj.signalException("signalArgumentError:", "" + val);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "charValue:")
  public abstract static class CharValuePrim extends UnaryExpressionNode {

    @Specialization
    public final long doString(final String c) {
      if (c == null || c.length() != 1) {
        KernelObj.signalException("signalArgumentError:", c);
        return -1;
      }

      return c.charAt(0);
    }

    @Specialization
    public final void doGeneric(final Object obj) {
      KernelObj.signalException("signalArgumentError:", obj);
    }
  }
}
