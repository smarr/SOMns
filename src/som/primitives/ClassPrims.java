package som.primitives;

import som.interpreter.nodes.PrimitiveNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public class ClassPrims {

  public abstract static class NamePrim extends PrimitiveNode {
    public NamePrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SAbstractObject doGeneric(final VirtualFrame frame,
        final SAbstractObject receiver, final Object arguments) {
      return ((SClass) receiver).getName();
    }
  }

  public abstract static class SuperClassPrim extends PrimitiveNode {
    public SuperClassPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SAbstractObject doGeneric(final VirtualFrame frame,
        final SAbstractObject receiver, final Object arguments) {
      return ((SClass) receiver).getSuperClass();
    }
  }

  public abstract static class InstanceInvokablesPrim extends PrimitiveNode {
    public InstanceInvokablesPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SAbstractObject doGeneric(final VirtualFrame frame,
        final SAbstractObject receiver, final Object arguments) {
      return ((SClass) receiver).getInstanceInvokables();
    }
  }

  public abstract static class InstanceFieldsPrim extends PrimitiveNode {
    public InstanceFieldsPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SAbstractObject doGeneric(final VirtualFrame frame,
        final SAbstractObject receiver, final Object arguments) {
      return ((SClass) receiver).getInstanceFields();
    }
  }
}
