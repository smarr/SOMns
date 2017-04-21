package som.interop;

import java.util.Map.Entry;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.SomLanguage;
import som.interpreter.Types;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.interpreter.nodes.dispatch.DispatchGuard;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vm.VmSettings;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;


public abstract class InteropDispatch extends Node {
  public static final int INLINE_CACHE_SIZE = VmSettings.DYNAMIC_METRICS ? 100 : 6;

  public abstract Object executeDispatch(VirtualFrame frame, String selector,
      Object[] args);

  protected static DispatchGuard createGuard(final Object rcvr) {
    return DispatchGuard.create(rcvr);
  }

  protected static SSymbol lookupWithPrefix(final String prefix, SClass cls, final int numArgs) {
    Dispatchable disp = null;
    while (disp == null && cls != null) {
      for (Entry<SSymbol, Dispatchable> e : cls.getDispatchables().entrySet()) {
        SSymbol sel = e.getKey();
        if (sel.getNumberOfSignatureArguments() == numArgs &&
            sel.getString().startsWith(prefix)) {
          return sel;
        }
      }
      cls = cls.getSuperClass();
    }
    return null;
  }

  protected AbstractMessageSendNode createSend(final String selector, final Object[] args) {
    SClass cls = Types.getClassOf(args[0]);
    SSymbol firstFit = lookupWithPrefix(selector, cls, args.length);

    // this is to avoid changing any standard logic just for interop
    ExpressionNode[] dummyNodes = new ExpressionNode[args.length];
    dummyNodes[0] = new DummyExpr();

//    return MessageSendNode.createMessageSend(firstFit, dummyNodes, null);
    return MessageSendNode.createForPerformNodes(firstFit, null,
        getRootNode().getLanguage(SomLanguage.class).getVM());
  }

  @Specialization(guards = {"selector == cachedSelector"},
      limit = "INLINE_CACHE_SIZE")
  public static Object cachedDispatch(final VirtualFrame frame,
      final String selector, final Object[] args,
      @Cached("selector") final String cachedSelector,
      @Cached("createSend(selector, args)") final AbstractMessageSendNode send) {
    return send.doPreEvaluated(frame, args);
  }

  private static final class DummyExpr extends ExpressionNode {

    DummyExpr() { super((SourceSection) null); }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      CompilerAsserts.neverPartOfCompilation("This is a dummy that never should be reachable");
      throw new RuntimeException("Should never be executed");
    }

    @Override
    public void markAsVirtualInvokeReceiver() { }
  }
}
