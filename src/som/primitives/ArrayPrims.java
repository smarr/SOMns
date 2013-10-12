package som.primitives;

import som.interpreter.nodes.PrimitiveNode;
import som.vm.Universe;
import som.vmobjects.SArray;
import som.vmobjects.SInteger;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public class ArrayPrims {
  public abstract static class AtPrim extends PrimitiveNode {

    public AtPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      SInteger index = (SInteger) ((SObject[]) arguments)[0];
      SArray   arr   = (SArray)   receiver;

      return arr.getIndexableField(index.getEmbeddedInteger() - 1);
    }
  }

  public abstract static class AtPutPrim extends PrimitiveNode {
    public AtPutPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      SObject  value = ((SObject[]) arguments)[1];
      SInteger index = (SInteger) ((SObject[]) arguments)[0];
      SArray   arr   = (SArray)   receiver;

      arr.setIndexableField(index.getEmbeddedInteger() - 1, value);
      return value;
    }
  }

  public abstract static class LengthPrim extends PrimitiveNode {
    public LengthPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      SArray arr = (SArray) receiver;
      return universe.newInteger(arr.getNumberOfIndexableFields());
    }
  }

  public abstract static class NewPrim extends PrimitiveNode {
    public NewPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      SInteger length = (SInteger) ((SObject[]) arguments)[0];
      return universe.newArray(length.getEmbeddedInteger());
    }
  }

}
