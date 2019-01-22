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
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.EventualSendNode;
import som.vm.VmSettings;
import tools.asyncstacktraces.ShadowStackEntry.EntryAtMessageSend;
import tools.asyncstacktraces.ShadowStackEntry.EntryForPromiseResolution;
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

  protected abstract StackFrameDescription getNextOnStack();

  protected abstract StackFrame createFirstStackFrame();

  @Override
  public StackFrame next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    if (!VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      return rawNext();
    } else if (!VmSettings.ACTOR_ASYNC_STACK_TRACE_METHOD_CACHE) {
      return nextAsyncStackStructureMethodCache();
    } else {
      return nextAsyncStackStructure();
    }
  }

  public StackFrame rawNext() {
    StackFrameDescription localFrame = getNextOnStack();
    return new StackFrame(localFrame.getRootNode().getName(), localFrame.getRootNode(),
        localFrame.getSourceSection(), localFrame.getFrame(), false);
  }

  public StackFrame nextAsyncStackStructure() {

    ShadowStackEntry shadow = null;
    boolean isFirst = first;

    Frame localFrame = null;
    boolean usedAgain = false;
    if (isFirst) {
      // Local Stack iterator is used to get first frame only.
      localFrame = getNextOnStack().getFrame();
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
      localFrame = getNextOnStack().getFrame();
    }
    return createStackFrame(shadow, localFrame, usedAgain);
  }

  public StackFrame nextAsyncStackStructureMethodCache() {
    return createStackFrame(null, null, false);
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

  public static final class SuspensionStackIterator extends StackIterator {
    private final Iterator<DebugStackFrame> localStack;
    private DebugStackFrame                 firstFrame;

    public SuspensionStackIterator(final Iterator<DebugStackFrame> localStack) {
      assert localStack != null;
      assert localStack.hasNext();

      this.localStack = localStack;
    }

    @Override
    protected StackFrameDescription getNextOnStack() {
      if (localStack.hasNext()) {
        DebugStackFrame frame = localStack.next();
        if (first) {
          firstFrame = frame;
        }
        return new StackFrameDescription(frame.getSourceSection(), frame.getFrame(),
            frame.getRootNode());
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
    private final FrameInstance firstFrame;
    private final SourceSection firstSourceSection;

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

    public HaltStackIterator(final SourceSection firstSourceSection) {
      this.firstSourceSection = firstSourceSection;
      firstFrame = getTopFrame();
    }

    @Override
    protected StackFrameDescription getNextOnStack() {
      if (first) {
        return new StackFrameDescription(firstFrame.getCallNode().getSourceSection(),
            firstFrame.getFrame(FrameAccess.READ_ONLY),
            firstFrame.getCallNode().getRootNode());
      } else {
        return null;
      }
    }

    @Override
    protected StackFrame createFirstStackFrame() {
      RootCallTarget ct = (RootCallTarget) firstFrame.getCallTarget();
      return new StackFrame(ct.getRootNode().getName(), ct.getRootNode(), firstSourceSection,
          firstFrame.getFrame(FrameAccess.READ_ONLY), false);
    }
  }
}
