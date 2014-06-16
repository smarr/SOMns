package som.interpreter.nodes.dispatch;

import som.vm.Universe;
import som.vmobjects.SSymbol;


public abstract class AbstractDispatchWithLookupNode extends
    AbstractDispatchNode {

  protected final SSymbol  selector;
  protected final Universe universe;

  public AbstractDispatchWithLookupNode(final SSymbol selector,
      final Universe universe, final boolean executesEnforced) {
    super(executesEnforced);
    this.selector = selector;
    this.universe = universe;
  }
}
