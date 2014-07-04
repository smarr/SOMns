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
      // Won't use DNU caching here, because it is already a megamorphic node
      CompilerAsserts.neverPartOfCompilation("GenericDispatchNode");
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
