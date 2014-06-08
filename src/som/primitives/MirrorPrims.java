package som.primitives;

import som.interpreter.nodes.nary.BinaryExpressionNode.BinarySideEffectFreeExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public final class MirrorPrims {
  public abstract static class DomainOfPrim extends BinarySideEffectFreeExpressionNode {
    @Specialization
    public final SObject doSClass(final SClass clazz, final SObject obj) {
      return obj.getDomain();
    }
  }

  public abstract static class SetDomainOfPrim extends TernaryExpressionNode {
    @Specialization
    public final SClass doSClass(final SClass clazz, final SObject obj,
        final SObject domain) {
      obj.setDomain(domain);
      return clazz;
    }
  }

  public abstract static class CurrentDomainPrim extends UnarySideEffectFreeExpressionNode {
    @Specialization
    public final SObject doSClass(final VirtualFrame frame, final SClass clazz) {
      return frame.getCurrentDomain();
    }
  }

  public abstract static class EvaluatedInPrim extends TernaryExpressionNode {
    @Specialization
    public final Object doSClass(final SClass clazz, final SBlock block, final SObject domain) {
      return block.getMethod().invoke(new Object[] {domain});
    }
  }

  public abstract static class EvaluatedEnforcedInPrim extends TernaryExpressionNode {
    @Specialization
    public final Object doSClass(final SClass clazz, final SBlock block, final SObject domain) {
      return block.getMethod().invoke(new Object[] {domain});
    }
  }
}
