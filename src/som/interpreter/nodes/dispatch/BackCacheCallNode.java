package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

import som.interpreter.Invokable;
import som.interpreter.Method;
import som.interpreter.SArguments;
import som.vm.VmSettings;
import tools.asyncstacktraces.ShadowStackEntry;
import tools.asyncstacktraces.ShadowStackEntryLoad;

public interface BackCacheCallNode {

    static void initializeUniqueCaller(final RootCallTarget methodCallTarget,
                                       final BackCacheCallNode node) {
        RootNode root = methodCallTarget.getRootNode();
        if (root instanceof Method) {
            ((Method) root).setNewCaller(node);
        }
    }

    static void setShadowStackEntry(final VirtualFrame frame,
                                    final boolean uniqueCaller, final Object[] arguments,
                                    final Node expression,
                                    final ShadowStackEntryLoad shadowStackEntryLoad) {
        if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
            assert arguments[arguments.length - 1] == null;
            assert (frame.getArguments()[frame.getArguments().length
                    - 1] instanceof ShadowStackEntry);
            assert frame.getArguments().length >= 2;
        }
        if (VmSettings.ACTOR_ASYNC_STACK_TRACE_METHOD_CACHE) {
            if (uniqueCaller) {
                SArguments.setShadowStackEntry(arguments, SArguments.getShadowStackEntry(frame));
            } else {
                SArguments.setShadowStackEntryWithCache(arguments, expression,
                        shadowStackEntryLoad, frame, false);
            }
        } else if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
            SArguments.setShadowStackEntryWithCache(arguments, expression,
                    shadowStackEntryLoad, frame, false);
        }
        assert arguments[arguments.length - 1] != null
                || (frame.getArguments()[frame.getArguments().length - 1] == null)
                || !VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE;
    }

    void makeUniqueCaller();

    void makeMultipleCaller();

    Invokable getCachedMethod();
}
