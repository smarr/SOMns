package som.primitives;

import som.compiler.Tags;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryBasicOperation;
import som.vm.Symbols;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import dym.Tagging;


public class StringPrims {

  @GenerateNodeFactory
  @Primitive("string:concat:")
  public abstract static class ConcatPrim extends BinaryComplexOperation {
    protected ConcatPrim(final SourceSection source) {
      super(Tagging.cloneAndAddTags(source, Tags.STRING_ACCESS));
    }

    @Specialization
    public final String doString(final String receiver, final String argument) {
      return receiver + argument;
    }

    @Specialization
    public final String doString(final String receiver, final SSymbol argument) {
      return receiver + argument.getString();
    }

    @Specialization
    public final String doSSymbol(final SSymbol receiver, final String argument) {
      return receiver.getString() + argument;
    }

    @Specialization
    public final String doSSymbol(final SSymbol receiver, final SSymbol argument) {
      return receiver.getString() + argument.getString();
    }
  }

  @GenerateNodeFactory
  @Primitive("stringAsSymbol:")
  public abstract static class AsSymbolPrim extends UnaryBasicOperation {
    public AsSymbolPrim(final SourceSection source) { super(Tagging.cloneAndAddTags(source, Tags.STRING_ACCESS)); }

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
  @Primitive("string:substringFrom:to:")
  public abstract static class SubstringPrim extends TernaryExpressionNode {
    private static final String[] STRING_PRIM = new String[] {Tags.COMPLEX_PRIMITIVE_OPERATION, Tags.STRING_ACCESS};
    private static final String[] NOT_A = new String[] {Tags.UNSPECIFIED_INVOKE};

    public SubstringPrim(final SourceSection source) { super(Tagging.cloneAndUpdateTags(source, STRING_PRIM, NOT_A)); }

    @Specialization
    public final String doString(final String receiver, final long start,
        final long end) {
      try {
        return receiver.substring((int) start - 1, (int) end);
      } catch (IndexOutOfBoundsException e) {
        return "Error - index out of bounds";
      }
    }

    @Specialization
    public final String doSSymbol(final SSymbol receiver, final long start,
        final long end) {
      return doString(receiver.getString(), start, end);
    }
  }
}
