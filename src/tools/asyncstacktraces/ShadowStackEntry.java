package tools.asyncstacktraces;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.Invokable;
import som.interpreter.actors.Actor;
import som.interpreter.actors.Actor.ActorProcessingThread;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualSendNode;
import tools.debugger.frontend.ApplicationThreadStack.StackFrame;


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

  public static ShadowStackEntry createRoot() {
    return new ShadowStackEntry(null, null);
  }

  public static ShadowStackEntry create(final ShadowStackEntry previous,
      final Node expr) {
    // TODO: assert previous != null;
    return new ShadowStackEntry(previous, unwrapNodeIfNecessary(expr));
  }

  public static ShadowStackEntry createAtAsyncSend(final ShadowStackEntry previous,
      final Node expr) {
    // TODO: assert previous != null;
    return new EntryAtMessageSend(previous, unwrapNodeIfNecessary(expr));
  }

  public static ShadowStackEntry createAtPromiseResolution(final ShadowStackEntry previous,
      final Node expr) {
    // TODO: assert previous != null;
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

  private static final class EntryAtMessageSend extends ShadowStackEntry {

    private EntryAtMessageSend(final ShadowStackEntry previous, final Node expr) {
      super(previous, expr);
    }

    @Override
    public boolean isAsync() {
      return true;
    }
  }

  private static final class EntryForPromiseResolution extends ShadowStackEntry {
    private EntryForPromiseResolution(final ShadowStackEntry previous,
        final Node expr) {
      super(previous, expr);
    }

    @Override
    public boolean isAsync() {
      return true;
    }
  }

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
  abstract static class StackIterator implements Iterator<StackFrame> {

    private ShadowStackEntry current;
    protected boolean        first;

    private ShadowStackEntry useAgain;
    private Frame            useAgainFrame;

    StackIterator() {
      current = null;
      first = true;
    }

    @Override
    public boolean hasNext() {
      return current != null || first || useAgain != null;
    }

    protected abstract Frame getNextOnStack();

    protected abstract StackFrame createFirstStackFrame();

    @Override
    public StackFrame next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      ShadowStackEntry shadow = null;
      boolean isFirst = first;

      Frame localFrame = null;
      boolean usedAgain = false;
      if (isFirst) {
        localFrame = getNextOnStack();

        // From now on, use shadow stack and local stack iterator together.
        // Though, at some point, we won't have info on local stack anymore, when going
        // to remote/async stacks.
        Object[] args = localFrame.getArguments();
        assert args[args.length - 1] instanceof ShadowStackEntry;
        current = (ShadowStackEntry) args[args.length - 1];

        first = false;
      } else if (useAgain != null) {
        shadow = useAgain;
        usedAgain = true;
        localFrame = useAgainFrame;

        useAgain = null;
        useAgainFrame = null;
      } else {
        shadow = current;
        if (shadow != null) {
          current = shadow.previous;
        }
        localFrame = getNextOnStack();
      }

      if (isFirst) {
        assert shadow == null
            && localFrame != null : "the shadow stack always starts with the caller this means for the top, we assemble a stack frame independent of it";
        return createFirstStackFrame();
      } else {
        return createStackFrame(shadow, localFrame, usedAgain);
      }
    }

    private StackFrame createStackFrame(final ShadowStackEntry shadow,
        final Frame localFrame, final boolean usedAgain) {
      assert shadow != null : "Since it is not the first stack element (we handled that above), we expect a shadow entry here";

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

      StackFrame result = new StackFrame(name, shadow.getRootNode(),
          location, localFrame, contextTransitionElement);

      return result;
    }
  }

  public static final class SuspensionStackIterator extends StackIterator {
    private final Iterator<DebugStackFrame> localStack;
    private DebugStackFrame                 firstFrame;

    public SuspensionStackIterator(final Iterator<DebugStackFrame> localStack) {
      assert localStack != null;
      assert localStack.hasNext();

      this.localStack = localStack;
    }

    @Override
    protected MaterializedFrame getNextOnStack() {
      if (localStack.hasNext()) {
        DebugStackFrame frame = localStack.next();

        if (first) {
          firstFrame = frame;
        }

        return frame.getFrame();
      } else {
        return null;
      }
    }

    @Override
    protected StackFrame createFirstStackFrame() {
      String name = firstFrame.getRootNode().getName();
      return new StackFrame(name, firstFrame.getRootNode(), firstFrame.getSourceSection(),
          firstFrame.getFrame(), false);
    }
  }

  public static final class HaltStackIterator extends StackIterator {
    private static FrameInstance getTopFrame() {
      FrameInstance result =
          Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<FrameInstance>() {
            @Override
            public FrameInstance visitFrame(final FrameInstance frame) {
              return frame;
            }
          });
      return result;
    }

    private final FrameInstance firstFrame;
    private final SourceSection firstSourceSection;

    public HaltStackIterator(final SourceSection firstSourceSection) {
      this.firstSourceSection = firstSourceSection;
      firstFrame = getTopFrame();
    }

    @Override
    protected Frame getNextOnStack() {
      if (first) {
        return firstFrame.getFrame(FrameAccess.READ_ONLY);
      } else {
        return null;
      }
    }

    @Override
    protected StackFrame createFirstStackFrame() {
      RootCallTarget ct = (RootCallTarget) firstFrame.getCallTarget();
      Invokable m = (Invokable) ct.getRootNode();

      String name = ct.getRootNode().getName();
      return new StackFrame(name, ct.getRootNode(), firstSourceSection,
          firstFrame.getFrame(FrameAccess.READ_ONLY), false);
    }
  }
}
