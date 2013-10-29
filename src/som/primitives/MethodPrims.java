package som.primitives;

import som.interpreter.nodes.PrimitiveNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public class MethodPrims {

  public abstract static class SignaturePrim extends PrimitiveNode {
    public SignaturePrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SAbstractObject doGeneric(final VirtualFrame frame,
        final SAbstractObject receiver, final Object arguments) {
      return ((SMethod) receiver).getSignature();
    }
  }

  public abstract static class HolderPrim extends PrimitiveNode {
    public HolderPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SAbstractObject doGeneric(final VirtualFrame frame,
        final SAbstractObject receiver, final Object arguments) {
      return ((SMethod) receiver).getHolder();
    }
  }

}
