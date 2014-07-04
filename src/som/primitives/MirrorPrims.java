package som.primitives;

import som.interpreter.SArguments;
import som.interpreter.nodes.nary.BinaryExpressionNode.BinarySideEffectFreeExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public final class MirrorPrims {
  public abstract static class DomainOfPrim extends BinarySideEffectFreeExpressionNode {
    public DomainOfPrim(final boolean executesEnforced) { super(executesEnforced); }
    public DomainOfPrim(final DomainOfPrim node) { super(node.executesEnforced); }

    @Specialization
    public final SObject doSClass(final SClass clazz, final SObject obj) {
      return obj.getDomain();
    }
  }

  public abstract static class SetDomainOfPrim extends TernaryExpressionNode {
    public SetDomainOfPrim(final boolean executesEnforced) { super(executesEnforced); }
    public SetDomainOfPrim(final SetDomainOfPrim node) { super(node.executesEnforced); }

    @Specialization
    public final SClass doSClass(final SClass clazz, final SObject obj,
        final SObject domain) {
      obj.setDomain(domain);
      return clazz;
    }

    @Specialization
    public final SClass doSClass(final SClass clazz, final Object[] arr,
        final SObject domain) {
      SArray.setOwner(arr, domain);
      return clazz;
    }
  }

  public abstract static class CurrentDomainPrim extends UnarySideEffectFreeExpressionNode {
    public CurrentDomainPrim(final boolean executesEnforced) { super(executesEnforced); }
    public CurrentDomainPrim(final CurrentDomainPrim node) { super(node.executesEnforced); }

    @Specialization
    public final SObject doSClass(final VirtualFrame frame, final SClass clazz) {
      return SArguments.domain(frame);
    }
  }

  public abstract static class EvaluatedInPrim extends TernaryExpressionNode {
    public EvaluatedInPrim(final boolean executesEnforced) { super(executesEnforced); }
    public EvaluatedInPrim(final EvaluatedInPrim node) { super(node.executesEnforced); }

    @Specialization
    public final Object doSClass(final VirtualFrame frame, final SClass clazz, final SBlock block, final SObject domain) {
      CompilerAsserts.neverPartOfCompilation("EvaluatedInPrim");
      boolean enforced = SArguments.enforced(frame);
      return block.getMethod().invoke(domain, enforced, new Object[] {block});
    }
  }

  public abstract static class EvaluatedEnforcedInPrim extends TernaryExpressionNode {
    public EvaluatedEnforcedInPrim(final boolean executesEnforced) { super(executesEnforced); }
    public EvaluatedEnforcedInPrim(final EvaluatedEnforcedInPrim node) { super(node.executesEnforced); }

    @Specialization
    public final Object doSClass(final VirtualFrame frame, final SClass clazz, final SBlock block, final SObject domain) {
      CompilerAsserts.neverPartOfCompilation("EvaluatedEnforcedInPrim");
      return block.getMethod().invoke(domain, true, new Object[] {block});
    }
  }

  public abstract static class ExecutesEnforcedPrim extends UnarySideEffectFreeExpressionNode {
    public ExecutesEnforcedPrim(final boolean executesEnforced) { super(executesEnforced); }
    public ExecutesEnforcedPrim(final ExecutesEnforcedPrim node) { super(node.executesEnforced); }

    @Specialization
    public final boolean doSClass(final VirtualFrame frame, final SClass clazz) {
      return SArguments.enforced(frame);
    }
  }

  public abstract static class ExecutesUnenforcedPrim extends UnarySideEffectFreeExpressionNode {
    public ExecutesUnenforcedPrim(final boolean executesEnforced) { super(executesEnforced); }
    public ExecutesUnenforcedPrim(final ExecutesUnenforcedPrim node) { super(node.executesEnforced); }

    @Specialization
    public final boolean doSClass(final VirtualFrame frame, final SClass clazz) {
      return !SArguments.enforced(frame);
    }
  }
}
