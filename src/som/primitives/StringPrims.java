package som.primitives;

import som.interpreter.nodes.messages.BinaryMonomorphicNode;
import som.interpreter.nodes.messages.TernaryMonomorphicNode;
import som.interpreter.nodes.messages.UnaryMonomorphicNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SInteger;
import som.vmobjects.SMethod;
import som.vmobjects.SString;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public class StringPrims {

  public abstract static class ConcatPrim extends BinaryMonomorphicNode {
    public ConcatPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public ConcatPrim(final ConcatPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization
    public SAbstractObject doSString(final SString receiver,
        final SString argument) {
      return universe.newString(receiver.getEmbeddedString()
          + argument.getEmbeddedString());
    }
  }

  public abstract static class AsSymbolPrim extends UnaryMonomorphicNode {
    public AsSymbolPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public AsSymbolPrim(final AsSymbolPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization
    public SAbstractObject doSString(final SString receiver) {
      return universe.symbolFor(receiver.getEmbeddedString());
    }
  }

  public abstract static class SubstringPrim extends TernaryMonomorphicNode {
    public SubstringPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public SubstringPrim(final SubstringPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization
    public SAbstractObject doSString(final SString receiver, final SInteger start, final SInteger end) {
      try {
        return universe.newString(receiver.getEmbeddedString().substring(
            start.getEmbeddedInteger() - 1, end.getEmbeddedInteger()));
      } catch (IndexOutOfBoundsException e) {
        return universe.newString(new String("Error - index out of bounds"));
      }
    }
  }
}
