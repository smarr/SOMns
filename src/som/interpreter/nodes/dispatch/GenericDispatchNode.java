package som.interpreter.nodes.dispatch;

import som.interpreter.Types;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;

public final class GenericDispatchNode extends AbstractDispatchWithLookupNode {
  @Child private IndirectCallNode call;

  public GenericDispatchNode(final SSymbol selector) {
    super(selector);
    call = Truffle.getRuntime().createIndirectCallNode();
  }

  @Override
  public Object executeDispatch(
      final VirtualFrame frame, final Object[] arguments) {
    Object rcvr = CompilerDirectives.unsafeCast(arguments[0], Object.class, true, true);
    SInvokable method = lookupMethod(rcvr);
    if (method != null) {
      return call.call(frame, method.getCallTarget(), arguments);
    } else {
      // Won't use DNU caching here, because it is already a megamorphic node
      CompilerAsserts.neverPartOfCompilation("GenericDispatchNode");
      return SAbstractObject.sendDoesNotUnderstand(selector, arguments);
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
