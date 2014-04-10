package som.interpreter.nodes.dispatch;

import som.vm.Universe;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;

public final class GenericDispatchNode extends AbstractDispatchWithLookupNode {

  public GenericDispatchNode(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  @Override
  public Object executeDispatch(final Object[] arguments) {
    SInvokable method = lookupMethod(arguments[0]);
    if (method != null) {
      return method.getCallTarget().call(arguments);
    } else {
      // TODO: perhaps, I should mark this branch with a branch profile as
      //       being unlikely
      return sendDoesNotUnderstand(arguments);
    }
  }

  private Object sendDoesNotUnderstand(final Object[] arguments) {
    // TODO: this is all extremely expensive, and could be optimized by
    //       further specialization for #dnu

    // Need to realize full SOM objects because we'll pass them on to a
    // language level array
//    SAbstractObject[] args = new SAbstractObject[arguments.length];
//    for (int i = 0; i < arguments.length; i++) {
//      args[i] = Types.asAbstractObject(arguments[i], universe);
//    }
//
//    return SAbstractObject.sendDoesNotUnderstand(selector, args, universe);
    throw new RuntimeException("Recheck implementation, do we really need to convert here? and what's with the receiver?");
  }
}
