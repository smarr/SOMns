package som.interpreter.nodes.dispatch;

import som.interpreter.Types;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class GenericDispatchNode extends AbstractDispatchWithLookupNode {

  public GenericDispatchNode(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  @Override
  public Object executeDispatch(
      final VirtualFrame frame, final Object[] arguments) {
    SInvokable method = lookupMethod(arguments[0]);
    if (method != null) {
      return method.invoke(arguments);
    } else {
      // TODO: perhaps, I should mark this branch with a branch profile as
      //       being unlikely
      return sendDoesNotUnderstand(arguments);
    }
  }

  @SlowPath
  private SInvokable lookupMethod(final Object rcvr) {
    SClass rcvrClass = Types.getClassOf(rcvr, universe);
    return rcvrClass.lookupInvokable(selector);
  }

  @SlowPath
  private Object sendDoesNotUnderstand(final Object[] arguments) {
    // TODO: this is all extremely expensive, and could be optimized by
    //       further specialization for #dnu
    return SAbstractObject.sendDoesNotUnderstand(selector, arguments, universe);
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1000;
  }
}
