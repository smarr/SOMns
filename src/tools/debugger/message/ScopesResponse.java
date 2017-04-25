package tools.debugger.message;

import java.util.ArrayList;

import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.RootNode;

import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.Method;
import som.interpreter.Primitive;
import som.interpreter.SArguments;
import som.interpreter.actors.ReceivedRootNode;
import som.vmobjects.SBlock;
import tools.TraceData;
import tools.debugger.frontend.Suspension;
import tools.debugger.message.Message.Response;


@SuppressWarnings("unused")
public final class ScopesResponse extends Response {
  private final Scope[] scopes;
  private final long variablesReference;

  private ScopesResponse(final long globalFrameId, final Scope[] scopes,
      final int requestId) {
    super(requestId);
    assert TraceData.isWithinJSIntValueRange(globalFrameId);
    this.variablesReference = globalFrameId;
    this.scopes = scopes;
  }

  private static final class Scope {
    /** Name of the scope such as 'Arguments', 'Locals'. */
    private final String name;

    /**
     * The variables of this scope can be retrieved by passing the value of
     * variablesReference to the VariablesRequest.
     */
    private final long variablesReference;

    /** If true, the number of variables in this scope is large or expensive to retrieve. */
    private final boolean expensive;


    private Scope(final String name, final long globalVarRef,
        final boolean expensive) {
      assert TraceData.isWithinJSIntValueRange(globalVarRef);
      this.name               = name;
      this.variablesReference = globalVarRef;
      this.expensive          = expensive;
    }
  }

  private static void addScopes(final ArrayList<Scope> scopes,
      final MethodScope method, final Object rcvr, final Suspension suspension) {
    MethodScope outer = method.getOuterMethodScopeOrNull();
    if (outer != null) {
      assert rcvr instanceof SBlock;
      MaterializedFrame mFrame = ((SBlock) rcvr).getContext();
      long globalScopeId = suspension.addScope(mFrame, outer);
      scopes.add(new Scope(outer.getMethod().getName(), globalScopeId, false));
    }
  }

  private static final int SMALL_INITIAL_SIZE = 5;

  public static ScopesResponse create(final long globalFrameId, final Suspension suspension,
      final int requestId) {
    DebugStackFrame frame = suspension.getFrame(globalFrameId);
    ArrayList<Scope> scopes = new ArrayList<>(SMALL_INITIAL_SIZE);
    MaterializedFrame mFrame = frame.getFrame();

    RootNode invokable = frame.getRootNode();
    if (invokable instanceof Method) {
      Method m = (Method) invokable;
      MethodScope scope = m.getLexicalScope();
      long scopeId = suspension.addScope(mFrame, scope);
      scopes.add(new Scope("Locals", scopeId, false));

      Object rcvr = SArguments.rcvr(mFrame);
      addScopes(scopes, scope, rcvr, suspension);
    } else if (invokable instanceof ReceivedRootNode) {
      // NOOP, no scopes here
      assert false : "This should not be reached. This scope should never get an id";
    } else {
      assert invokable instanceof Primitive :
        "Got a " + invokable.getClass().getSimpleName() +
        " here. Means we need to add support";
    }

    return new ScopesResponse(globalFrameId, scopes.toArray(new Scope[0]), requestId);
  }
}
