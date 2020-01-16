package tools.asyncstacktraces;

import com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.Actor;
import som.interpreter.actors.Actor.ActorProcessingThread;
import som.interpreter.actors.EventualMessage;
import som.vm.VmSettings;

public class ShadowStackEntry {

    protected final ShadowStackEntry previous;
    protected final Node             expression;

    public static long numberOfAllocations;

    public static final boolean ALLOCATION_COUNT = false;

    public Node getExpression() {
        return expression;
    }

    public ShadowStackEntry getPreviousShadowStackEntry() {
        return previous;
    }

    public static ShadowStackEntry createTop(final Node expr) {
        return new ShadowStackEntry(null, expr);
    }

    public static ShadowStackEntry create(final ShadowStackEntry previous,
                                          final Node expr) {
        assert !VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE || previous != null;
        return new ShadowStackEntry(previous, unwrapNodeIfNecessary(expr));
    }

    public static ShadowStackEntry createAtAsyncSend(final ShadowStackEntry previous,
                                                     final Node expr) {
        assert !VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE || previous != null;
        return new EntryAtMessageSend(previous, unwrapNodeIfNecessary(expr));
    }

    public static ShadowStackEntry createAtPromiseResolution(final ShadowStackEntry previous,
                                                             final Node expr) {
        assert !VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE || previous != null;
        return new EntryForPromiseResolution(previous, unwrapNodeIfNecessary(expr));
    }

    public static Node unwrapNodeIfNecessary(final Node node) {
        if (node instanceof WrapperNode) {
            return ((WrapperNode) node).getDelegateNode();
        } else {
            return node;
        }
    }

    protected ShadowStackEntry(final ShadowStackEntry previous, final Node expr) {
        assert VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE;
        this.previous = previous;
        this.expression = expr;
        if (ALLOCATION_COUNT) {
            numberOfAllocations++;
        }
    }

    public static Actor getCurrentActorOrNull() {
        Thread t = Thread.currentThread();
        if (t instanceof ActorProcessingThread) {
            return EventualMessage.getActorCurrentMessageIsExecutionOn();
        } else {
            return null;
        }
    }

    public RootNode getRootNode() {
        return expression.getRootNode();
    }

    public SourceSection getSourceSection() {
        return expression.getSourceSection();
    }

    public boolean isAsync() {
        return false;
    }

    public static final class EntryAtMessageSend extends ShadowStackEntry {

        private EntryAtMessageSend(final ShadowStackEntry previous, final Node expr) {
            super(previous, expr);
        }

        @Override
        public boolean isAsync() {
            return true;
        }
    }

    public static final class EntryForPromiseResolution extends ShadowStackEntry {
        private EntryForPromiseResolution(final ShadowStackEntry previous,
                                          final Node expr) {
            super(previous, expr);
        }

        @Override
        public boolean isAsync() {
            return true;
        }
    }
}
