package som.interpreter.nodes.dispatch;

import som.interpreter.SArguments;
import som.interpreter.Types;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;

public class GenericDispatchNode extends AbstractDispatchWithLookupNode {

  public GenericDispatchNode(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object rcvr,
      final Object[] arguments) {
    SInvokable method = lookupMethod(rcvr);
    if (method != null) {
      return method.getCallTarget().call(frame.pack(), new SArguments(rcvr,
          arguments));
    } else {
      // TODO: perhaps, I should mark this branch with a branch profile as
      //       being unlikely
      return sendDoesNotUnderstand(frame, rcvr, arguments);
    }
  }

  private Object sendDoesNotUnderstand(final VirtualFrame frame,
      final Object rcvr, final Object[] arguments) {
    // TODO: this is all extremely expensive, and could be optimized by
    //       further specialization for #dnu

    // Need to realize full SOM objects because we'll pass them on to a
    // language level array
    SAbstractObject[] args = new SAbstractObject[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      args[i] = Types.asAbstractObject(arguments[i], universe);
    }

    return SAbstractObject.sendDoesNotUnderstand(rcvr, selector, args,
        universe, frame.pack());
  }
}
