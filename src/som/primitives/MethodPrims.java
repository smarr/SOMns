package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SInvokable;

import com.oracle.truffle.api.dsl.Specialization;


public final class MethodPrims {

  public abstract static class SignaturePrim extends UnarySideEffectFreeExpressionNode {
    public SignaturePrim() { super(false); } /* TODO: enforced!!! */

    @Specialization
    public final SAbstractObject doSMethod(final SInvokable receiver) {
      return receiver.getSignature();
    }
  }

  public abstract static class HolderPrim extends UnarySideEffectFreeExpressionNode {
    public HolderPrim() { super(false); } /* TODO: enforced!!! */
    @Specialization
    public final SAbstractObject doSMethod(final SInvokable receiver) {
      return receiver.getHolder();
    }
  }
}
