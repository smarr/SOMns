package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.vmobjects.SAbstractObject;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class HashPrim extends UnarySideEffectFreeExpressionNode {
  public HashPrim() { super(false); } /* TODO: enforced!!! */

  @Specialization
  public final long doSString(final String receiver) {
    return receiver.hashCode();
  }

  @Specialization
  public final long doSAbstractObject(final SAbstractObject receiver) {
    return receiver.hashCode();
  }
}
