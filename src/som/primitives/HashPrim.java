package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.vmobjects.SAbstractObject;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class HashPrim extends UnarySideEffectFreeExpressionNode {
  @Specialization
  public final int doSString(final String receiver) {
    return receiver.hashCode();
  }

  @Specialization
  public final int doSAbstractObject(final SAbstractObject receiver) {
    return receiver.hashCode();
  }
}
