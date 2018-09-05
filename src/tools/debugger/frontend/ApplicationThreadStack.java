package tools.debugger.frontend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.LexicalScope.MethodScope;
import tools.asyncstacktraces.ShadowStackEntry;


/**
 * Keeps information on the run-time stack of an application thread for
 * requests from the front-end. Is populated only on demand.
 */
public class ApplicationThreadStack {

  /**
   * Track scopes that contain variables as well as objects.
   * These have been identified in the debugger, i.e., they got an id for
   * direct access.
   */
  final ArrayList<Object>     scopesAndObjects;
  final HashMap<Object, Long> scopesAndObjectsSet;

  private final ArrayList<StackFrame> stackFrames;
  private final SuspendedEvent        event;
  private final Suspension            suspension;

  public static final class StackFrame {
    public final RootNode          root;
    public final SourceSection     section;
    public final MaterializedFrame frame;
    public final boolean           asyncSeparator;

    private StackFrame(final RootNode root, final SourceSection section,
        final MaterializedFrame frame, final boolean asyncSeparator) {
      this.root = root;
      this.section = section;
      this.frame = frame;
      this.asyncSeparator = asyncSeparator;
    }
  }

  ApplicationThreadStack(final SuspendedEvent event, final Suspension suspension) {
    this.event = event;
    this.stackFrames = new ArrayList<>();
    this.scopesAndObjects = new ArrayList<>();
    this.scopesAndObjectsSet = new HashMap<>();
    this.suspension = suspension;
  }

  ArrayList<StackFrame> get() {
    if (stackFrames.isEmpty()) {
      Iterator<DebugStackFrame> stack = event.getStackFrames().iterator();

      DebugStackFrame top = stack.next();

      // the shadow stack always points to the caller
      // this means for the top, we assemble a stack frame independent of it
      MaterializedFrame topFrame = top.getFrame();
      stackFrames.add(
          new StackFrame(top.getRootNode(), top.getSourceSection(), topFrame, false));

      // for the rest of the stack, we use shadow stack and local stack iterator
      // but at some point, we won't have info on local stack anymore, when going
      // to remote/async stacks
      Object[] args = topFrame.getArguments();
      assert args[args.length - 1] instanceof ShadowStackEntry;
      ShadowStackEntry currentFrame = (ShadowStackEntry) args[args.length - 1];

      while (currentFrame != null) {
        MaterializedFrame frame = null;
        if (stack.hasNext()) {
          frame = stack.next().getFrame();
        }

        stackFrames.add(new StackFrame(currentFrame.getRootNode(),
            currentFrame.getSourceSection(), frame, false));

        if (currentFrame.isAsync()) {
          stackFrames.add(new StackFrame(currentFrame.getRootNode(), null, null, true));
        }

        currentFrame = currentFrame.getPrevious();
      }

      assert !stackFrames.isEmpty() : "We expect that there is always at least one stack frame";
    }
    return stackFrames;
  }

  long addScope(final MaterializedFrame frame,
      final MethodScope lexicalScope) {
    scopesAndObjects.add(new RuntimeScope(frame, lexicalScope));
    return getLastScopeOrVarId();
  }

  long addObject(final Object obj) {
    Long idx = scopesAndObjectsSet.get(obj);
    if (idx == null) {
      scopesAndObjects.add(obj);
      idx = getLastScopeOrVarId();
      scopesAndObjectsSet.put(obj, idx);
    }
    return idx;
  }

  Object getScopeOrObject(final int localVarRef) {
    // need to subtract 1, because getLastScopeOrVarId uses size
    // instead of size - 1 for id, because VS code does not allow 0 as id
    return scopesAndObjects.get(localVarRef - 1);
  }

  private long getLastScopeOrVarId() {
    // using size() means ids start with 1, which seems to be needed
    // otherwise, VS code ignores the top frame
    return suspension.getGlobalId(scopesAndObjects.size());
  }
}
