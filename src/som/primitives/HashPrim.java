package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SAbstractObject;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class HashPrim extends UnaryExpressionNode {
  @Specialization
  public int doSString(final String receiver) {
    return receiver.hashCode();
  }

  @Specialization
  public int doSAbstractObject(final SAbstractObject receiver) {
    return receiver.hashCode();
  }
}
