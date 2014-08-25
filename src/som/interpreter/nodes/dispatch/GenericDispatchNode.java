package som.interpreter.nodes.dispatch;

import som.interpreter.Types;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class GenericDispatchNode extends AbstractDispatchWithLookupNode {

  public GenericDispatchNode(final SSymbol selector) {
    super(selector);
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final SObject domain,
      final boolean enforced, final Object[] arguments) {
    Object rcvr = CompilerDirectives.unsafeCast(arguments[0], Object.class, true, true);
    SInvokable method = lookupMethod(rcvr);
    if (method != null) {
      return method.invoke(domain, enforced, arguments);
    } else {
      // Won't use DNU caching here, because it is already a megamorphic node
      CompilerAsserts.neverPartOfCompilation("GenericDispatchNode");
      return SAbstractObject.sendDoesNotUnderstand(selector, arguments, domain,
          enforced);
    }
  }

  @SlowPath
  private SInvokable lookupMethod(final Object rcvr) {
    SClass rcvrClass = Types.getClassOf(rcvr);
    return rcvrClass.lookupInvokable(selector);
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1000;
  }
}
