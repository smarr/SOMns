package som.primitives;

import som.interpreter.nodes.PrimitiveNode;
import som.vm.Universe;
import som.vmobjects.SInteger;
import som.vmobjects.SObject;
import som.vmobjects.SString;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public class StringPrims {

  public abstract static class ConcatPrim extends PrimitiveNode {
    public ConcatPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      SString argument = (SString) ((SObject[]) arguments)[0];
      SString self = (SString) receiver;
      return universe.newString(self.getEmbeddedString()
          + argument.getEmbeddedString());
    }
  }

  public abstract static class AsSymbolPrim extends PrimitiveNode {
    public AsSymbolPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      SString self = (SString) receiver;
      return universe.symbolFor(self.getEmbeddedString());
    }
  }

  public abstract static class LengthPrim extends PrimitiveNode {
    public LengthPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      SString self = (SString) receiver;
      return universe.newInteger(self.getEmbeddedString().length());
    }
  }

  public abstract static class EqualsPrim extends PrimitiveNode {
    public EqualsPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      SObject op1 = ((SObject[]) arguments)[0];
      SString op2 = (SString) receiver;
      if (op1.getSOMClass() == universe.stringClass) {
        SString s = (SString) op1;
        if (s.getEmbeddedString().equals(op2.getEmbeddedString())) {
          return universe.trueObject;
        }
      }

      return universe.falseObject;
    }
  }

  public abstract static class SubstringPrim extends PrimitiveNode {
    public SubstringPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      SInteger end   = (SInteger) ((SObject[]) arguments)[1];
      SInteger start = (SInteger) ((SObject[]) arguments)[0];

      SString self = (SString) receiver;

      try {
        return universe.newString(self.getEmbeddedString().substring(
            start.getEmbeddedInteger() - 1, end.getEmbeddedInteger()));
      } catch (IndexOutOfBoundsException e) {
        return universe.newString(new String(
            "Error - index out of bounds"));
      }
    }
  }

  public abstract static class HashPrim extends PrimitiveNode {
    public HashPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      SString self = (SString) receiver;
      return universe.newInteger(self.getEmbeddedString().hashCode());
    }
  }
}
