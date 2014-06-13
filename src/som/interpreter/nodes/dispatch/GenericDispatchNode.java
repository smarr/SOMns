package som.interpreter.nodes.dispatch;

import som.interpreter.SArguments;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class GenericDispatchNode extends AbstractDispatchWithLookupNode {

  public GenericDispatchNode(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  @Override
  public Object executeDispatch(
      final VirtualFrame frame, final Object[] arguments) {
    SInvokable method = lookupMethod(arguments[0]);
    SObject domain = SArguments.domain(frame);
    boolean enforced = SArguments.enforced(frame);
    if (method != null) {
      return method.invoke(domain, enforced, arguments);
    } else {
      // TODO: should we use the DNU handling infrastructure here?
      CompilerAsserts.neverPartOfCompilation();
      return SAbstractObject.sendDoesNotUnderstand(selector, arguments, domain, enforced, universe);
    }
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1000;
  }
}
