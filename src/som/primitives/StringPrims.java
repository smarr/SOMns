package som.primitives;

import som.interpreter.nodes.BinaryMessageNode;
import som.interpreter.nodes.TernaryMessageNode;
import som.interpreter.nodes.UnaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public class StringPrims {

  public abstract static class ConcatPrim extends BinaryMessageNode {
    public ConcatPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public ConcatPrim(final ConcatPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public String doSString(final String receiver, final String argument) {
      return receiver + argument;
    }
  }

  public abstract static class AsSymbolPrim extends UnaryMessageNode {
    public AsSymbolPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public AsSymbolPrim(final AsSymbolPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public SAbstractObject doSString(final String receiver) {
      return universe.symbolFor(receiver);
    }
  }

  public abstract static class SubstringPrim extends TernaryMessageNode {
    public SubstringPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public SubstringPrim(final SubstringPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public String doSString(final String receiver, final int start, final int end) {
      try {
        return receiver.substring(start - 1, end);
      } catch (IndexOutOfBoundsException e) {
        return "Error - index out of bounds";
      }
    }
  }
}
