package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.impl.DefaultCallTarget;
import com.oracle.truffle.api.nodes.RootNode;

import som.interpreter.Method;
import som.interpreter.Primitive;
import som.vm.VmSettings;


// CallBehaviorInterface
public interface ShadowStackEntryMethodCacheCompatibleNode {

  static boolean requiresShadowStack(final DefaultCallTarget actualMethodCallTarget,
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

  void uniqueCaller();

  void multipleCaller();

  Method getCachedMethod();
}
