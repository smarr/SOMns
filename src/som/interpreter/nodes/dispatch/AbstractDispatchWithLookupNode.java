package som.interpreter.nodes.dispatch;

import som.interpreter.SArguments;
import som.interpreter.Types;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;


public abstract class AbstractDispatchWithLookupNode extends
    AbstractDispatchNode {

  protected final SSymbol  selector;
  protected final Universe universe;

  public AbstractDispatchWithLookupNode(final SSymbol selector,
      final Universe universe) {
    super();
    this.selector = selector;
    this.universe = universe;
  }

  protected final SMethod lookupMethod(final SArguments arguments) {
    SClass rcvrClass = Types.getClassOf(arguments.getReceiver(), universe);
    return rcvrClass.lookupInvokable(selector);
  }
}
