package tools.debugger.message;

import java.util.ArrayList;

import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.RootNode;

import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.Method;
import som.interpreter.Primitive;
import som.interpreter.SArguments;
import som.vmobjects.SBlock;
import tools.debugger.frontend.Suspension;
import tools.debugger.message.Message.Response;


@SuppressWarnings("unused")
public final class ScopesResponse extends Response {
  private final Scope[] scopes;
  private final int variablesReference;

  private ScopesResponse(final int frameId, final Scope[] scopes,
      final int requestId) {
    super(requestId);
    this.variablesReference = frameId;
    this.scopes = scopes;
  }

  private static final class Scope {
    /** Name of the scope such as 'Arguments', 'Locals'. */
    private final String name;

    /**
     * The variables of this scope can be retrieved by passing the value of
     * variablesReference to the VariablesRequest.
     */
    private final int variablesReference;

    /** If true, the number of variables in this scope is large or expensive to retrieve. */
    private final boolean expensive;


    private Scope(final String name, final int variableReference,
        final boolean expensive) {
      this.name               = name;
      this.variablesReference = variableReference;
      this.expensive          = expensive;
    }
  }

  private static void addScopes(final ArrayList<Scope> scopes,
      final MethodScope method, final Object rcvr, final Suspension suspension) {
    MethodScope outer = method.getOuterMethodScopeOrNull();
    if (outer != null) {
      assert rcvr instanceof SBlock;
      MaterializedFrame mFrame = ((SBlock) rcvr).getContext();
      int scopeId = suspension.addScope(mFrame, outer);
      scopes.add(new Scope(outer.getMethod().getName(), scopeId, false));
    }
  }

  private static final int SMALL_INITIAL_SIZE = 5;

  public static ScopesResponse create(final int frameId, final Suspension suspension,
      final int requestId) {
    DebugStackFrame frame = suspension.getFrame(frameId);
    ArrayList<Scope> scopes = new ArrayList<>(SMALL_INITIAL_SIZE);
    MaterializedFrame mFrame = frame.getFrame();

    RootNode invokable = frame.getRootNode();
    if (invokable instanceof Method) {
      Method m = (Method) invokable;
      MethodScope scope = m.getLexicalScope();
      int scopeId = suspension.addScope(mFrame, scope);
      scopes.add(new Scope("Locals", scopeId, false));

      Object rcvr = mFrame.getArguments()[SArguments.RCVR_IDX];
      addScopes(scopes, scope, rcvr, suspension);
    } else {
      assert invokable instanceof Primitive :
        "Got a " + invokable.getClass().getSimpleName() +
        " here. Means we need to add support";
    }

    return new ScopesResponse(frameId, scopes.toArray(new Scope[0]), requestId);
  }
}
