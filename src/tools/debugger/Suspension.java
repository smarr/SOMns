package tools.debugger;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;

import som.compiler.Variable.Argument;
import som.interpreter.LexicalScope.MethodScope;
import som.primitives.ObjectPrims.HaltPrim;

/**
 * Suspension controls the interaction between the debugger front-end and the
 * application thread.
 *
 * <p>It provides a simple mechanism to ask the application thread to perform
 * tasks on behalf of the front-end. This is useful for instance to walk the
 * stack and obtain the relevant data for the debugger.
 */
public class Suspension {
  public final int activityId;
  private final WeakReference<Object> activity;
  private final ArrayBlockingQueue<Task> tasks;

  private SuspendedEvent suspendedEvent;
  private Stack stack;

  Suspension(final WeakReference<Object> activity, final int activityId) {
    this.activity   = activity;
    this.activityId = activityId;
    this.tasks = new ArrayBlockingQueue<>(2);
  }

  synchronized void update(final SuspendedEvent e) {
    this.suspendedEvent = e;
    this.stack = new Stack(e);
  }

  public synchronized ArrayList<DebugStackFrame> getStackFrames() {
    return stack.get();
  }

  public synchronized int addScope(final MaterializedFrame frame,
      final MethodScope lexicalScope) {
    stack.scopesAndObjects.add(new RuntimeScope(frame, lexicalScope));
    return getLastScopeOrVarId();
  }

  public synchronized int addObject(final Object obj) {
    Integer idx = stack.scopesAndObjectsSet.get(obj);
    if (idx == null) {
      stack.scopesAndObjects.add(obj);
      idx = getLastScopeOrVarId();
      stack.scopesAndObjectsSet.put(obj, idx);
    }
    return idx;
  }

  private int getLastScopeOrVarId() {
    // this means ids start with 1, which seems to be needed
    // otherwise, VS code ignores the top frame
    return getGlobalId(stack.scopesAndObjects.size());
  }

  public synchronized boolean isHaltPrimitive() {
    return suspendedEvent.getNode() instanceof HaltPrim;
  }

  private static final int BITS_FOR_LOCAL_ID = 20;
  private static final int LOCAL_ID_MASK = (1 << BITS_FOR_LOCAL_ID) - 1;

  public int getGlobalId(final int localId) {
    final int maxId = 1 << BITS_FOR_LOCAL_ID;
    assert localId < maxId;
    assert ((long) activityId << BITS_FOR_LOCAL_ID) < Integer.MAX_VALUE;
    return (activityId << BITS_FOR_LOCAL_ID) + localId;
  }

  public int getLocalId(final int globalId) {
    assert (globalId >> BITS_FOR_LOCAL_ID) == activityId :
      "should be an id for current activity, otherwise, something is wrong";
    return globalId & LOCAL_ID_MASK;
  }

  public static int getActivityIdFromGlobalId(final int globalId) {
    return globalId >> BITS_FOR_LOCAL_ID;
  }

  public synchronized DebugStackFrame getFrame(final int globalId) {
    return stack.get().get(getLocalId(globalId));
  }

  public synchronized Object getScopeOrObject(final int globalVarRef) {
    // need to subtract 1, because getLastScopeOrVarId uses size
    // instead of size - 1 for id, because VS code does not allow 0 as id
    return stack.scopesAndObjects.get(getLocalId(globalVarRef) - 1);
  }

  public static final int FRAMES_SKIPPED_FOR_HALT = 2;

  /**
   * Instruct the suspended thread to resume.
   */
  public void resume() {
    submitTask(new Resume());
  }

  /**
   * Instruct the suspended task to send its stack trace to the front-end.
   */
  public void sendStackTrace(final int startFrame, final int levels,
      final int requestId, final FrontendConnector frontend) {
    submitTask(new SendStackTrace(startFrame, levels, frontend, this, requestId));
  }

  public void sendScopes(final int frameId, final FrontendConnector frontend,
      final int requestId) {
    frontend.sendScopes(frameId, this, requestId);
  }

  public void sendVariables(final int varRef, final FrontendConnector frontend, final int requestId) {
    frontend.sendVariables(varRef, requestId, this);
  }

  private void submitTask(final Task task) {
    try {
      tasks.put(task);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Suspend the current thread, and process tasks from the front-end.
   */
  public void suspend() {
    boolean continueWaiting = true;
    while (continueWaiting) {
      try {
        continueWaiting = tasks.take().execute();
      } catch (InterruptedException e) { /* Just continue waiting */ }
    }
    synchronized (this) {
      suspendedEvent = null;
      stack = null;
    }
  }

  public Object getActivity() { return activity.get(); }
  public synchronized SuspendedEvent getEvent() { return suspendedEvent; }

  private abstract static class Task {
    /**
     * @return continue waiting on true, resume execution on false.
     */
    abstract boolean execute();
  }

  private static class Resume extends Task {
    @Override
    boolean execute() { return false; }
  }

  private static class SendStackTrace extends Task {
    private final int startFrame;
    private final int levels;
    private final int requestId;
    private final FrontendConnector frontend;
    private final Suspension suspension;

    SendStackTrace(final int startFrame, final int levels,
        final FrontendConnector frontend, final Suspension suspension,
        final int requestId) {
      this.startFrame = startFrame;
      this.levels     = levels;
      this.frontend   = frontend;
      this.suspension = suspension;
      this.requestId  = requestId;
    }

    @Override
    boolean execute() {
      frontend.sendStackTrace(startFrame, levels, suspension, requestId);
      return true;
    }
  }

  public static class RuntimeScope {
    private final MaterializedFrame frame;
    private final MethodScope lexicalScope;

    RuntimeScope(final MaterializedFrame frame, final MethodScope lexcialScope) {
      this.frame = frame;
      this.lexicalScope = lexcialScope;
      assert frame.getFrameDescriptor() == lexcialScope.getFrameDescriptor();
    }

    public Argument[] getArguments() {
      return lexicalScope.getMethod().getArguments();
    }

    public Object getArgument(final int idx) {
      return frame.getArguments()[idx];
    }

    public List<? extends FrameSlot> getLocals() {
      return lexicalScope.getFrameDescriptor().getSlots();
    }

    public Object getLocal(final FrameSlot slot) {
      return frame.getValue(slot);
    }
  }

  private static class Stack {

    /**
     * Track scopes that contain variables as well as objects.
     * These have been identified in the debugger, i.e., they got an id for
     * direct access.
     */
    private final ArrayList<Object> scopesAndObjects;
    private final HashMap<Object, Integer> scopesAndObjectsSet;

    private final ArrayList<DebugStackFrame> stackFrames;
    private final SuspendedEvent event;


    Stack(final SuspendedEvent event) {
      this.event = event;
      this.stackFrames = new ArrayList<>();
      this.scopesAndObjects = new ArrayList<>();
      this.scopesAndObjectsSet = new HashMap<>();
    }

    ArrayList<DebugStackFrame> get() {
      if (stackFrames.isEmpty()) {
        for (DebugStackFrame frame : event.getStackFrames()) {
          stackFrames.add(frame);
        }
        assert !stackFrames.isEmpty() : "We expect that there is always at least one stack frame";
      }
      return stackFrames;
    }
  }
}
