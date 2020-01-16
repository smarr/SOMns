package tools.asyncstacktraces;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.Invokable;
import som.interpreter.Method;
import som.interpreter.actors.EventualSendNode;
import som.interpreter.nodes.dispatch.BackCacheCallNode;
import som.vm.VmSettings;
import tools.asyncstacktraces.ShadowStackEntry.EntryAtMessageSend;
import tools.asyncstacktraces.ShadowStackEntry.EntryForPromiseResolution;
import tools.asyncstacktraces.StackIterator.ShadowStackIterator.HaltShadowStackIterator;
import tools.asyncstacktraces.StackIterator.ShadowStackIterator.SuspensionShadowStackIterator;
import tools.debugger.frontend.ApplicationThreadStack.StackFrame;


/**
 * This iterator traverses the run time stack and all available calling context
 * information.
 *
 * <p>
 * In special cases, a single stack frame/calling context might be
 * represented as multiple stack frames in the iteration.
 * We chose to do this to explicitly represent context switches in asynchronous
 * control flow, as caused for instance by eventual message sends or promise resolution.
 */

public abstract class StackIterator implements Iterator<StackFrame> {

    @Override
    public abstract boolean hasNext();

    @Override
    public abstract StackFrame next();

    protected StackFrame createStackFrame(final Frame localFrame, final RootNode rootNode,
                                          final String name, final SourceSection location) {
        return new StackFrame(name, rootNode,
                location, localFrame, false);
    }

    public static StackIterator createHaltIterator() {
        if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
            return new HaltShadowStackIterator();
        } else {
            return new HaltIterator();
        }
    }

    public static StackIterator createSuspensionIterator(
            final Iterator<DebugStackFrame> localStack) {
        if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
            return new SuspensionShadowStackIterator(localStack);
        } else {
            return new SuspensionIterator(localStack);
        }
    }

    public static class SuspensionIterator extends StackIterator {

        protected ArrayList<DebugStackFrame> frames;
        protected int                        currentIndex = 0;

        public SuspensionIterator(final Iterator<DebugStackFrame> localStack) {
            assert localStack != null;
            while (localStack.hasNext()) {
                frames.add(localStack.next());
            }
        }

        @Override
        public boolean hasNext() {
            return currentIndex != frames.size();
        }

        @Override
        public StackFrame next() {
            DebugStackFrame next = frames.get(currentIndex);
            currentIndex++;
            return createStackFrame(next.getFrame(),
                    next.getRootNode(),
                    next.getRootNode().getName(),
                    next.getSourceSection());
        }
    }

    public static class HaltIterator extends StackIterator {

        protected ArrayList<FrameInstance> frameInstances;
        protected int                      currentIndex = 0;

        public HaltIterator() {
            frameInstances = new ArrayList<FrameInstance>();
            Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Object>() {
                @Override
                public Object visitFrame(final FrameInstance frameInstance) {
                    frameInstances.add(frameInstance);
                    return null;
                }
            });
        }

        @Override
        public boolean hasNext() {
            return currentIndex != frameInstances.size();
        }

        @Override
        public StackFrame next() {
            FrameInstance next = frameInstances.get(currentIndex);
            currentIndex++;
            RootCallTarget rt = (RootCallTarget) next.getCallTarget();
            if (!(rt.getRootNode() instanceof Invokable)) {
                return null;
            }
            Invokable rootNode = (Invokable) rt.getRootNode();
            SourceSection ss = null;
            if (next.getCallNode() != null) {
                ss = next.getCallNode().getSourceSection();
            }
            return createStackFrame(next.getFrame(FrameAccess.READ_ONLY),
                    rootNode,
                    rootNode.getName(),
                    ss);
        }
    }

    /**
     *
     * @author clementbera
     *
     *         By contrast to HaltIterator and Suspension Iterator, the ShadowStackIterator use
     *         the stack for the first frame only and then rely on shadow stack entry to get the
     *         following stack entries
     */
    public abstract static class ShadowStackIterator extends StackIterator {
        private ShadowStackEntry currentSSEntry;
        protected boolean        first;
        private Node             currentNode;
        private Invokable        currentMethod;

        private ShadowStackEntry useAgain;
        private Frame            useAgainFrame;

        public ShadowStackIterator() {
            currentSSEntry = null;
            first = true;
        }

        @Override
        public boolean hasNext() {
            return currentSSEntry != null || first || useAgain != null;
        }

        protected abstract StackFrameDescription getFirstFrame();

        @Override
        public StackFrame next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            if (VmSettings.ACTOR_ASYNC_STACK_TRACE_METHOD_CACHE) {
                return nextAsyncStackStructureMethodCache();
            } else {
                return nextAsyncStackStructure();
            }
        }

        protected StackFrame nextAsyncStackStructure() {

            ShadowStackEntry shadow = null;
            Frame localFrame = null;
            boolean usedAgain = false;

            if (first) {
                localFrame = getFirstFrame().getFrame();
                Object[] args = localFrame.getArguments();
                assert args[args.length - 1] instanceof ShadowStackEntry;
                currentSSEntry = (ShadowStackEntry) args[args.length - 1];
                first = false;
            } else if (useAgain != null) {
                shadow = useAgain;
                usedAgain = true;
                localFrame = useAgainFrame;
                useAgain = null;
                useAgainFrame = null;
            } else {
                shadow = currentSSEntry;
                if (shadow != null) {
                    currentSSEntry = shadow.previous;
                }
            }
            return createStackFrame(shadow, localFrame, usedAgain);
        }

        protected StackFrame createStackFrame(final ShadowStackEntry shadow,
                                              final Frame localFrame, final boolean usedAgain) {
            if (shadow == null) {
                return null;
            }
            if (shadow.expression == null) {
                return null;
            }
            boolean contextTransitionElement;

            String name = shadow.getRootNode().getName();
            SourceSection location;

            if (!usedAgain && (shadow instanceof EntryAtMessageSend
                    || shadow instanceof EntryForPromiseResolution)) {
                contextTransitionElement = true;
                location = null;

                if (shadow instanceof EntryAtMessageSend) {
                    Node sendNode = shadow.expression.getParent();
                    if (sendNode instanceof EventualSendNode) {
                        name = "Send: " + ((EventualSendNode) sendNode).getSentSymbol();

                    } else {
                        name = "Send: " + name;
                    }
                    useAgain = shadow;
                    useAgainFrame = localFrame;
                } else if (shadow instanceof EntryForPromiseResolution) {
                    name = "Resolved: " + name;
                }
            } else {
                contextTransitionElement = false;
                location = shadow.getSourceSection();
            }

            return new StackFrame(name, shadow.getRootNode(),
                    location, localFrame, contextTransitionElement);
        }

        // This version has to skip missing shadow stack entry using method back pointer cache
        protected StackFrame nextAsyncStackStructureMethodCache() {
            ShadowStackEntry shadow = null;
            Frame localFrame = null;
            boolean usedAgain = false;

            if (first) {
                localFrame = getFirstFrame().getFrame();
                Object[] args = localFrame.getArguments();
                assert args[args.length - 1] instanceof ShadowStackEntry;
                currentSSEntry = (ShadowStackEntry) args[args.length - 1];
                currentMethod = (Invokable) getFirstFrame().getRootNode();
                first = false;
            }

            if (useAgain != null) {
                shadow = useAgain;
                usedAgain = true;
                localFrame = useAgainFrame;
                useAgain = null;
                useAgainFrame = null;
            } else {
                if (shouldUsePreviousShadowStackEntry(currentMethod,
                        currentSSEntry.getExpression())) {
                    shadow = currentSSEntry;
                    currentSSEntry = currentSSEntry.getPreviousShadowStackEntry();
                    // null if start frame
                    if (currentSSEntry != null) {
                        currentNode = currentSSEntry.getExpression();
                    }
                } else {
                    assert currentMethod instanceof Method;
                    currentNode = (Node) ((Method) currentMethod).getUniqueCaller();
                }
            }

            if (shadow != null) {
                return createStackFrame(shadow, localFrame, usedAgain);
            } else {
                return createStackFrame(localFrame, currentNode.getRootNode(),
                        currentNode.getRootNode().getName(), currentNode.getSourceSection());
            }
        }

        public boolean shouldUsePreviousShadowStackEntry(final Invokable currentMethod,
                                                         final Node prevExpression) {
            if (!(currentMethod instanceof Method)) {
                return true;
            }
            if (prevExpression instanceof BackCacheCallNode) {
                BackCacheCallNode ssNode =
                        (BackCacheCallNode) prevExpression;
                return currentMethod == ssNode.getCachedMethod();
            }
            return true;
        }

        protected static final class StackFrameDescription {
            SourceSection sourceSection;
            Frame         frame;
            RootNode      rootNode;

            public StackFrameDescription(final SourceSection sourceSection,
                                         final Frame frame, final RootNode rootNode) {
                this.sourceSection = sourceSection;
                this.frame = frame;
                this.rootNode = rootNode;
            }

            public SourceSection getSourceSection() {
                return sourceSection;
            }

            public Frame getFrame() {
                return frame;
            }

            public RootNode getRootNode() {
                return rootNode;
            }
        }

        public static final class SuspensionShadowStackIterator extends ShadowStackIterator {
            private final DebugStackFrame firstFrame;

            public SuspensionShadowStackIterator(final Iterator<DebugStackFrame> localStack) {
                assert localStack != null;
                assert localStack.hasNext();
                firstFrame = localStack.next();
            }

            @Override
            protected StackFrameDescription getFirstFrame() {
                assert first;
                return new StackFrameDescription(firstFrame.getSourceSection(), firstFrame.getFrame(),
                        firstFrame.getRootNode());
            }

        }

        public static final class HaltShadowStackIterator extends ShadowStackIterator {
            private final FrameInstance firstFrame;

            public HaltShadowStackIterator() {
                firstFrame =
                        Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<FrameInstance>() {
                            @Override
                            public FrameInstance visitFrame(final FrameInstance frame) {
                                return frame;
                            }
                        });
            }

            @Override
            protected StackFrameDescription getFirstFrame() {
                assert first;
                RootCallTarget rt = (RootCallTarget) firstFrame.getCallTarget();
                assert rt.getRootNode() instanceof Invokable;
                Invokable rootNode = (Invokable) rt.getRootNode();
                SourceSection ss = null;
                if (firstFrame.getCallNode() != null) {
                    ss = firstFrame.getCallNode().getSourceSection();
                }
                return new StackFrameDescription(ss,
                        firstFrame.getFrame(FrameAccess.READ_ONLY),
                        rootNode);
            }
        }
    }
}
