package som.interpreter.nodes.dispatch;

import som.interpreter.SArguments;
import som.interpreter.Types;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class GenericDispatchNode extends AbstractDispatchWithLookupNode {

  public GenericDispatchNode(final SSymbol selector, final Universe universe,
      final boolean executesEnforced) {
    super(selector, universe, executesEnforced);
  }

  @Override
  public Object executeDispatch(
      final VirtualFrame frame, final Object[] arguments) {
    SInvokable method = lookupMethod(arguments[0]);
    SObject domain = SArguments.domain(frame);
    if (method != null) {
      return method.invoke(domain, executesEnforced, arguments);
    } else {
      // Won't use DNU caching here, because it is already a megamorphic node
      CompilerAsserts.neverPartOfCompilation();
      return SAbstractObject.sendDoesNotUnderstand(selector, arguments, domain,
          executesEnforced, universe);
    }
  }

  @SlowPath
  private SInvokable lookupMethod(final Object rcvr) {
    SClass rcvrClass = Types.getClassOf(rcvr, universe);
    return rcvrClass.lookupInvokable(selector);
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1000;
  }
}
