package som.primitives;

import som.interpreter.SArguments;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


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

  public abstract static class InvokeOnPrim extends TernaryExpressionNode {
    public InvokeOnPrim() {
      super(false); /* TODO: enforced!!! */
    }

    @Specialization
    public final Object doInvoke(final VirtualFrame frame,
        final SInvokable receiver, final Object target, final Object[] argsArr) {
      CompilerAsserts.neverPartOfCompilation();
      SObject domain = SArguments.domain(frame);
      boolean enforced = SArguments.enforced(frame); // TODO: I should get that from the node instead of from the frame!
      return receiver.invoke(domain, enforced, SArray.fromSArrayToArgArrayWithReceiver(argsArr, target));
    }
  }
}
