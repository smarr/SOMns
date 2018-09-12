package tools.asyncstacktraces;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import som.Output;
import som.interpreter.actors.Actor;
import som.interpreter.actors.Actor.ActorProcessingThread;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualSendNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.SOMNode;
import tools.SourceCoordinate;
import tools.debugger.frontend.ApplicationThreadStack.StackFrame;


public class ShadowStackEntry {

  protected final ShadowStackEntry previous;
  protected final ExpressionNode   expression;

  public static long numberOfAllocations;

  public static final boolean ALLOCATION_COUNT = false;

  public static ShadowStackEntry createRoot() {
    return new ShadowStackEntry(null, null);
  }

  public static ShadowStackEntry create(final ShadowStackEntry previous,
      final ExpressionNode expr) {
    // TODO: assert previous != null;
    return new ShadowStackEntry(previous, SOMNode.unwrapIfNecessary(expr));
  }

  public static ShadowStackEntry createAtAsyncSend(final ShadowStackEntry previous,
      final ExpressionNode expr) {
    // TODO: assert previous != null;
    return new EntryAtMessageSend(previous, SOMNode.unwrapIfNecessary(expr));
  }

  public static ShadowStackEntry createAtPromiseResolution(final ShadowStackEntry previous,
      final ExpressionNode expr) {
    // TODO: assert previous != null;
    return new EntryForPromiseResolution(previous, SOMNode.unwrapIfNecessary(expr));
  }

  protected ShadowStackEntry(final ShadowStackEntry previous, final ExpressionNode expr) {
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

  public void printAsyncStackTrace() {
    ShadowStackEntry currentEntry = this;
    StringBuilder sb = new StringBuilder();
    Actor currentActor = getCurrentActorOrNull();
    while (currentEntry != null) {
      currentActor = currentEntry.printOn(sb, currentActor);
      currentEntry = currentEntry.previous;
    }
    Output.print(sb.toString());
  }

  public Actor printOn(final StringBuilder sb, final Actor currentActor) {
    SourceSection nodeSS = getSourceSection();
    String location =
        nodeSS.getSource().getName() + SourceCoordinate.getLocationQualifier(nodeSS);
    String method = getRootNode().getName();
    sb.append("   ");
    sb.append(method);
    sb.append(':');
    sb.append(location);
    sb.append('\n');
    return currentActor;
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
    protected final Actor sender;

    private EntryAtMessageSend(final ShadowStackEntry previous, final ExpressionNode expr) {
      super(previous, expr);
      this.sender = getCurrentActorOrNull();
    }

    @Override
    public Actor printOn(final StringBuilder sb, final Actor currentActor) {
      if (sender != currentActor) {
        sb.append(sender.toString());
        sb.append(" (hash=");
        sb.append(sender.hashCode());
        sb.append(")\n");
      }
      super.printOn(sb, currentActor);
      return sender;
    }

    @Override
    public boolean isAsync() {
      return true;
    }
  }

  private static final class EntryForPromiseResolution extends ShadowStackEntry {
    private EntryForPromiseResolution(final ShadowStackEntry previous,
        final ExpressionNode expr) {
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
  public static final class StackIterator implements Iterator<StackFrame> {

    private final Iterator<DebugStackFrame> localStack;

    private ShadowStackEntry current;
    private boolean          first;

    private ShadowStackEntry useAgain;
    private DebugStackFrame  useAgainFrame;
    // private boolean dummyConsumed;

    public StackIterator(final Iterator<DebugStackFrame> localStack) {
      assert localStack != null;
      assert localStack.hasNext();

      this.localStack = localStack;

      current = null;
      first = true;
    }

    @Override
    public boolean hasNext() {
      return current != null || first || useAgain != null;
    }

    @Override
    public StackFrame next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      ShadowStackEntry result = null;
      boolean isFirst = first;

      DebugStackFrame localFrame = null;
      boolean usedAgain = false;
      if (isFirst) {
        first = false;
        localFrame = localStack.next();

        // From now on, use shadow stack and local stack iterator together.
        // Though, at some point, we won't have info on local stack anymore, when going
        // to remote/async stacks.
        MaterializedFrame topFrame = localFrame.getFrame();
        Object[] args = topFrame.getArguments();
        assert args[args.length - 1] instanceof ShadowStackEntry;
        current = (ShadowStackEntry) args[args.length - 1];
      } else if (useAgain != null) {
        result = useAgain;
        usedAgain = true;
        localFrame = useAgainFrame;

        useAgain = null;
        useAgainFrame = null;
      } else {
        result = current;
        if (result != null) {
          current = result.previous;
        }
        if (localStack.hasNext()) {
          localFrame = localStack.next();
        }
      }

      return createStackFrame(result, localFrame, usedAgain);
    }

    private StackFrame createStackFrame(final ShadowStackEntry shadow,
        final DebugStackFrame localFrame, final boolean usedAgain) {
      if (shadow == null && localFrame != null) {
        // the shadow stack always starts with the caller
        // this means for the top, we assemble a stack frame independent of it
        String name = localFrame.getRootNode().getName();
        return new StackFrame(name, localFrame.getRootNode(), localFrame.getSourceSection(),
            localFrame.getFrame(), false);
      }

      assert shadow != null : "Since it is not the first stack element (we handled that above), we expect a shadow entry here";

      boolean contextTransitionElement;
      MaterializedFrame frame;

      String name = shadow.getRootNode().getName();
      SourceSection location;

      if (!usedAgain && (shadow instanceof EntryAtMessageSend
          || shadow instanceof EntryForPromiseResolution)) {
        contextTransitionElement = true;
        frame = null;
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
        frame = (localFrame == null) ? null : localFrame.getFrame();
        location = shadow.getSourceSection();
      }

      StackFrame result = new StackFrame(name, shadow.getRootNode(),
          location, frame, contextTransitionElement);

      return result;
    }
  }
}
