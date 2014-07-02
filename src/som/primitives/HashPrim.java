package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.vmobjects.SAbstractObject;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class HashPrim extends UnarySideEffectFreeExpressionNode {
  public HashPrim(final boolean executesEnforced) { super(executesEnforced); }
  public HashPrim(final HashPrim node) { this(node.executesEnforced); }

  @Specialization
  public final long doSString(final String receiver) {
    return receiver.hashCode();
  }

  @Specialization
  public final long doSAbstractObject(final SAbstractObject receiver) {
    return receiver.hashCode();
  }
}
