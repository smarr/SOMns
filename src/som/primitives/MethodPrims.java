package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SMethod;

import com.oracle.truffle.api.dsl.Specialization;


public class MethodPrims {

  public abstract static class SignaturePrim extends UnaryExpressionNode {
    @Specialization
    public SAbstractObject doSMethod(final SMethod receiver) {
      return receiver.getSignature();
    }
  }

  public abstract static class HolderPrim extends UnaryExpressionNode {
    @Specialization
    public SAbstractObject doSMethod(final SMethod receiver) {
      return receiver.getHolder();
    }
  }
}
