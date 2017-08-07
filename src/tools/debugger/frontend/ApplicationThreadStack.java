package tools.debugger.frontend;

import java.util.ArrayList;
import java.util.HashMap;

import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.MaterializedFrame;

import som.interpreter.LexicalScope.MethodScope;


/**
 * Keeps information on the run-time stack of an application thread for
 * requests from the front-end. Is populated only on demand.
 */
class ApplicationThreadStack {

  /**
   * Track scopes that contain variables as well as objects.
   * These have been identified in the debugger, i.e., they got an id for
   * direct access.
   */
  final ArrayList<Object>     scopesAndObjects;
  final HashMap<Object, Long> scopesAndObjectsSet;

  private final ArrayList<DebugStackFrame> stackFrames;
  private final SuspendedEvent             event;
  private final Suspension                 suspension;

  ApplicationThreadStack(final SuspendedEvent event, final Suspension suspension) {
    this.event = event;
    this.stackFrames = new ArrayList<>();
    this.scopesAndObjects = new ArrayList<>();
    this.scopesAndObjectsSet = new HashMap<>();
    this.suspension = suspension;
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
