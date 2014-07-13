package som.interpreter.nodes.dispatch;

import som.vmobjects.SSymbol;


public abstract class AbstractDispatchWithLookupNode extends
    AbstractDispatchNode {

  protected final SSymbol selector;

  public AbstractDispatchWithLookupNode(final SSymbol selector) {
    super();
    this.selector = selector;
  }
}
