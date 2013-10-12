package som.primitives;

import som.interpreter.nodes.PrimitiveNode;
import som.vm.Universe;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SInteger;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public class ObjectPrims {
  public abstract static class EqualsEqualsPrim extends PrimitiveNode {
    public EqualsEqualsPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame, final SObject receiver,
        final Object arguments) {
      if (receiver == ((SObject[]) arguments)[0]) {
        return universe.trueObject;
      } else {
        return universe.falseObject;
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
      return universe.newInteger(receiver.hashCode());
    }
  }

  public abstract static class ObjectSizePrim extends PrimitiveNode {
    public ObjectSizePrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      int size = receiver.getNumberOfFields();
      if (receiver instanceof SArray) {
        size += ((SArray) receiver).getNumberOfIndexableFields();
      }
      return universe.newInteger(size);

    }
  }

  public abstract static class PerformPrim extends PrimitiveNode {
    public PerformPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      SSymbol selector = (SSymbol) ((SObject[]) arguments)[0];

      SMethod invokable = receiver.getSOMClass().lookupInvokable(selector);
      return invokable.invoke(frame.pack(), receiver, null);
    }
  }

  public abstract static class PerformInSuperclassPrim extends PrimitiveNode {
    public PerformInSuperclassPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      SSymbol selector = (SSymbol) ((SObject[]) arguments)[0];
      SClass  clazz    = (SClass)  ((SObject[]) arguments)[1];

      SMethod invokable = clazz.lookupInvokable(selector);
      return invokable.invoke(frame.pack(), receiver, null);
    }
  }

  public abstract static class PerformWithArgumentsPrim extends PrimitiveNode {
    public PerformWithArgumentsPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      SSymbol selector = (SSymbol) ((SObject[]) arguments)[0];
      SArray  argsArr  = (SArray)  ((SObject[]) arguments)[1];

      SMethod invokable = receiver.getSOMClass().lookupInvokable(selector);
      return invokable.invoke(frame.pack(), receiver, argsArr.indexableFields);
    }
  }

  public abstract static class InstVarAtPrim extends PrimitiveNode {
    public InstVarAtPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      SInteger idx = (SInteger) ((SObject[]) arguments)[0];

      return receiver.getField(idx.getEmbeddedInteger() - 1);
    }
  }

  public abstract static class InstVarAtPutPrim extends PrimitiveNode {
    public InstVarAtPutPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      SInteger idx = (SInteger) ((SObject[]) arguments)[0];
      SObject val  = ((SObject[]) arguments)[1];

      receiver.setField(idx.getEmbeddedInteger() - 1, val);

      return val;
    }
  }

  public abstract static class HaltPrim extends PrimitiveNode {
    public HaltPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      Universe.errorPrintln("BREAKPOINT");
      return receiver;
    }
  }

  public abstract static class ClassPrim extends PrimitiveNode {
    public ClassPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      return receiver.getSOMClass();
    }
  }
}
