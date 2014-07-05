package som.primitives;

import som.interpreter.SArguments;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;


public final class MethodPrims {

  public abstract static class SignaturePrim extends UnarySideEffectFreeExpressionNode {
    public SignaturePrim(final boolean executesEnforced) { super(executesEnforced); }
    public SignaturePrim(final SignaturePrim node) { this(node.executesEnforced); }

    @Specialization
    public final SAbstractObject doSMethod(final SInvokable receiver) {
      return receiver.getSignature();
    }
  }

  public abstract static class HolderPrim extends UnarySideEffectFreeExpressionNode {
    public HolderPrim(final boolean executesEnforced) { super(executesEnforced); }
    public HolderPrim(final HolderPrim node) { this(node.executesEnforced); }

    @Specialization
    public final SAbstractObject doSMethod(final SInvokable receiver) {
      return receiver.getHolder();
    }
  }

  public abstract static class InvokeOnPrim extends TernaryExpressionNode {
    // TODO: figure out whether we should use an inline cache here
    @Child private IndirectCallNode callNode;

    public InvokeOnPrim(final boolean executesEnforced) {
      super(executesEnforced);
      callNode = Truffle.getRuntime().createIndirectCallNode();
    }
    public InvokeOnPrim(final InvokeOnPrim node) { this(node.executesEnforced); }

    @Specialization
    public final Object doInvoke(final VirtualFrame frame,
        final SInvokable receiver, final Object target, final Object[] argsArr) {
      SObject domain = SArguments.domain(frame);
      return callNode.call(frame, receiver.getCallTarget(),
          SArguments.createSArgumentsWithReceiver(domain, executesEnforced, target, argsArr));
    }
  }
}
