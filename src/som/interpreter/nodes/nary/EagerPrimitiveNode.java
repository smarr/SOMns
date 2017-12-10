package som.interpreter.nodes.nary;

import bd.nodes.EagerPrimitive;
import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SSymbol;
import tools.Send;


public abstract class EagerPrimitiveNode extends ExpressionNode
    implements EagerPrimitive, Send {
  protected final SSymbol selector;

  protected EagerPrimitiveNode(final SSymbol selector) {
    this.selector = selector;
  }

  public SSymbol getSelector() {
    return selector;
  }
}
