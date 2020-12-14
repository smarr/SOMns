package tools.debugger.frontend;

import java.util.ArrayList;
import java.util.HashMap;

import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import tools.debugger.asyncstacktraces.StackIterator;

import som.interpreter.LexicalScope.MethodScope;


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
    final ArrayList<Object> scopesAndObjects;
    final HashMap<Object, Long> scopesAndObjectsSet;

    private final ArrayList<StackFrame> stackFrames;
    private final SuspendedEvent event;
    private final Suspension suspension;

    public static final class StackFrame {
        public final String name;
        public final SourceSection section;
        public final Frame frame;
        public final boolean asyncOperation;
        private final RootNode root;

        public StackFrame(final String name, final RootNode root, final SourceSection section,
                          final Frame frame, final boolean asyncOperation) {
            this.name = name;
            this.root = root;
            this.section = section;
            this.frame = frame;
            this.asyncOperation = asyncOperation;
        }

        public RootNode getRootNode() {
            return root;
        }

        public boolean hasFrame() {
            return frame != null;
        }
    }

    ApplicationThreadStack(final Suspension suspension) {
        this.event = suspension.getEvent();
        this.stackFrames = new ArrayList<>();
        this.scopesAndObjects = new ArrayList<>();
        this.scopesAndObjectsSet = new HashMap<>();
        this.suspension = suspension;
    }

    ArrayList<StackFrame> get() {
        if (stackFrames.isEmpty()) {
            StackIterator stack =
                    StackIterator.createSuspensionIterator(event.getStackFrames().iterator(), suspension.getActivity().getId());

            if (stack instanceof StackIterator.ShadowStackIterator.SuspensionShadowStackIterator) {
                StackFrame topFrame = ((StackIterator.ShadowStackIterator.SuspensionShadowStackIterator) stack).getTopFrame();
                //get top frame first
                stackFrames.add(topFrame);
            }

            while (stack.hasNext()) {
                StackFrame next = stack.next();
                if (next != null) { //this validation is needed because at the moment ShadowStackIterator.next can return null values for null shadows
                    stackFrames.add(next);
                }
            }
            assert !stackFrames.isEmpty() : "We expect that there is always at least one stack frame";

        }
        return stackFrames;
    }

    long addScope(final Frame frame,
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
