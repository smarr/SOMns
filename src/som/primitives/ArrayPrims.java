package som.primitives;

import static som.vmobjects.SDomain.getDomainForNewObjects;
import som.interpreter.SArguments;
import som.interpreter.nodes.nary.BinaryExpressionNode.BinarySideEffectFreeExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vm.Universe;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public final class ArrayPrims {
  public abstract static class AtPrim extends BinarySideEffectFreeExpressionNode {
    public AtPrim() { super(false); /* TODO: enforced!!! */ }
    @Specialization
    public final Object doSArray(final Object[] receiver, final long argument) {
      return SArray.get(receiver, argument);
    }
  }

  public abstract static class AtPutPrim extends TernaryExpressionNode {
    public AtPutPrim() { super(false); /* TODO: enforced!!! */ }
    @Specialization
    public final Object doSArray(final Object[] receiver, final long index, final Object value) {
      SArray.set(receiver, index, value);
      return value;
    }
  }

  public abstract static class NewPrim extends BinarySideEffectFreeExpressionNode {
    private final Universe universe;
    public NewPrim() { super(false);  /* TODO: enforced!!! */ this.universe = Universe.current(); }

    protected final boolean receiverIsArrayClass(final SClass receiver) {
      return receiver == universe.arrayClass;
    }

    @Specialization(guards = "receiverIsArrayClass")
    public final Object[] doSClass(final VirtualFrame frame, final SClass receiver, final long length) {
      SObject domain = SArguments.domain(frame);
      return SArray.newSArray(length, universe.nilObject,
          getDomainForNewObjects(domain));
    }
  }
}
