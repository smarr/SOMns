package som.interop;

import java.util.Map.Entry;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

import som.VM;
import som.interpreter.Types;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.interpreter.nodes.dispatch.DispatchGuard;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vm.VmSettings;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;


public abstract class InteropDispatch extends Node {
  public static final int INLINE_CACHE_SIZE = VmSettings.DYNAMIC_METRICS ? 100 : 6;

  private final VM vm;

  public InteropDispatch(final VM vm) { this.vm = vm; }

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
    return MessageSendNode.createForPerformNodes(firstFit, null, vm);
  }

  @Specialization(guards = {"selector == cachedSelector"},
      limit = "INLINE_CACHE_SIZE")
  public static Object cachedDispatch(final VirtualFrame frame,
      final String selector, final Object[] args,
      @Cached("selector") final String cachedSelector,
      @Cached("createSend(selector, args)") final AbstractMessageSendNode send) {
    return send.doPreEvaluated(frame, args);
  }
}
