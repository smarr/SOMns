package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

import som.interpreter.Method;
import som.interpreter.Primitive;
import som.interpreter.SArguments;
import som.interpreter.actors.EventualMessage;
import som.vm.VmSettings;
import tools.asyncstacktraces.ShadowStackEntryLoad;


// CallBehaviorInterface
public interface ShadowStackEntryMethodCacheCompatibleNode {

  static boolean requiresShadowStack(final RootCallTarget actualMethodCallTarget,
      final ShadowStackEntryMethodCacheCompatibleNode node) {
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_METHOD_CACHE) {
      RootNode root = actualMethodCallTarget.getRootNode();
      if (root instanceof Method) {
        ((Method) root).setNewCaller(node);
        return true;
      } else if (root instanceof Primitive) {
        return false;
      } else {
        throw new Error("Unsupported entry so far. Can this happen?");
      }
    } else {
      return false;
    }
  }

  static void setShadowStackEntry(final VirtualFrame frame, final boolean requiresShadowStack,
      final boolean uniqueCaller, final Object[] arguments,
      final Node expression,
      final ShadowStackEntryLoad shadowStackEntryLoad) {
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_METHOD_CACHE) {
      if (requiresShadowStack) {
        if (uniqueCaller) {
          // At least two entries, receiver and ShadowStackEntry,
          // Except from VM main (start) which we heuristically assert against start string
          assert frame.getArguments().length >= 2 ||
              ((frame.getArguments()[0] instanceof EventualMessage.DirectMessage)
                  && (((EventualMessage.DirectMessage) frame.getArguments()[0])).getSelector()
                                                                                .getString()
                                                                                .equals(
                                                                                    "start"));
          SArguments.setShadowStackEntry(arguments, SArguments.getShadowStackEntry(frame));
        } else {
          SArguments.setShadowStackEntryWithCache(arguments, expression,
              shadowStackEntryLoad, frame, false);
        }
      }
    } else if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      SArguments.setShadowStackEntryWithCache(arguments, expression,
          shadowStackEntryLoad, frame, false);
    }
  }

  void uniqueCaller();

  void multipleCaller();

  Method getCachedMethod();
}
