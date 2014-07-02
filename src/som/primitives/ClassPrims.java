package som.primitives;

import som.interpreter.SArguments;
import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SDomain;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public class ClassPrims {

  public abstract static class NamePrim extends UnarySideEffectFreeExpressionNode {
    public NamePrim(final boolean executesEnforced) { super(executesEnforced); }
    public NamePrim(final NamePrim node) { super(node.executesEnforced); }

    @Specialization
    public final SAbstractObject doSClass(final SClass receiver) {
      return receiver.getName();
    }
  }

  public abstract static class SuperClassPrim extends UnarySideEffectFreeExpressionNode {
    public SuperClassPrim(final boolean executesEnforced) { super(executesEnforced); }
    public SuperClassPrim(final SuperClassPrim node) { super(node.executesEnforced); }

    @Specialization
    public final SAbstractObject doSClass(final SClass receiver) {
      return receiver.getSuperClass();
    }
  }

  public abstract static class InstanceInvokablesPrim extends UnarySideEffectFreeExpressionNode {
    public InstanceInvokablesPrim(final boolean executesEnforced) { super(executesEnforced); }
    public InstanceInvokablesPrim(final InstanceInvokablesPrim node) { super(node.executesEnforced); }

    @Specialization
    public final Object[] doSClass(final VirtualFrame frame, final SClass receiver) {
      SObject domain = SArguments.domain(frame);
      return SArray.newSArray(receiver.getInstanceInvokables(),
          SDomain.getDomainForNewObjects(domain));
    }
  }

  public abstract static class InstanceFieldsPrim extends UnarySideEffectFreeExpressionNode {
    public InstanceFieldsPrim(final boolean executesEnforced) { super(executesEnforced); }
    public InstanceFieldsPrim(final InstanceFieldsPrim node) { super(node.executesEnforced); }

    @Specialization
    public final Object[] doSClass(final VirtualFrame frame, final SClass receiver) {
      SObject domain = SArguments.domain(frame);
      return SArray.newSArray(receiver.getInstanceFields(),
          SDomain.getDomainForNewObjects(domain));
    }
  }
}
