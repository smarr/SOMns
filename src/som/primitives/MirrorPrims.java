package som.primitives;

import som.interpreter.SArguments;
import som.interpreter.nodes.nary.BinaryExpressionNode.BinarySideEffectFreeExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public final class MirrorPrims {
  public abstract static class DomainOfPrim extends BinarySideEffectFreeExpressionNode {
    public DomainOfPrim() { super(false); }/* TODO: enforced!!! */
    @Specialization
    public final SObject doSClass(final SClass clazz, final SObject obj) {
      return obj.getDomain();
    }
  }

  public abstract static class SetDomainOfPrim extends TernaryExpressionNode {
    public SetDomainOfPrim() { super(false); } /* TODO: enforced!!! */

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
    public CurrentDomainPrim() { super(false); } /* TODO: enforced!!! */
    @Specialization
    public final SObject doSClass(final VirtualFrame frame, final SClass clazz) {
      return SArguments.domain(frame);
    }
  }

  public abstract static class EvaluatedInPrim extends TernaryExpressionNode {
    public EvaluatedInPrim() { super(false); } /* TODO: enforced!!! */
    @Specialization
    public final Object doSClass(final VirtualFrame frame, final SClass clazz, final SBlock block, final SObject domain) {
      boolean enforced = SArguments.enforced(frame);
      return block.getMethod().invoke(domain, enforced, new Object[] {block});
    }
  }

  public abstract static class EvaluatedEnforcedInPrim extends TernaryExpressionNode {
    public EvaluatedEnforcedInPrim() { super(false); } /* TODO: enforced!!! */
    @Specialization
    public final Object doSClass(final VirtualFrame frame, final SClass clazz, final SBlock block, final SObject domain) {
      return block.getMethod().invoke(domain, true, new Object[] {block});
    }
  }

  public abstract static class ExecutesEnforcedPrim extends UnarySideEffectFreeExpressionNode {
    public ExecutesEnforcedPrim() { super(false); } /* TODO: enforced!!! */
    @Specialization
    public final boolean doSClass(final VirtualFrame frame, final SClass clazz) {
      return SArguments.enforced(frame);
    }
  }

  public abstract static class ExecutesUnenforcedPrim extends UnarySideEffectFreeExpressionNode {
    public ExecutesUnenforcedPrim() { super(false); } /* TODO: enforced!!! */
    @Specialization
    public final boolean doSClass(final VirtualFrame frame, final SClass clazz) {
      return !SArguments.enforced(frame);
    }
  }
}
