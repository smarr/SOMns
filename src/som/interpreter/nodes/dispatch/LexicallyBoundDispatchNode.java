package som.interpreter.nodes.dispatch;

import som.VM;
import som.compiler.Tags;
import som.instrumentation.InstrumentableDirectCallNode;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import dym.Tagging;


/**
 * Private methods are special, they are linked unconditionally to the call site.
 * Thus, we don't need to check at the dispatch whether they apply or not.
 */
public final class LexicallyBoundDispatchNode extends AbstractDispatchNode {

  @Child private DirectCallNode cachedMethod;

  private static final String[] DISP_NODE = new String[0]; // the dispatch node itself does not need any tags
  private static final String[] VIRTUAL_INVOKE = new String[] {Tags.CACHED_VIRTUAL_INVOKE};
  private static final String[] NOT_A = new String[] {Tags.LOOP_BODY, Tags.LOOP_NODE, Tags.UNSPECIFIED_INVOKE};

  public LexicallyBoundDispatchNode(final SourceSection source, final CallTarget methodCallTarget) {
    super(Tagging.cloneAndUpdateTags(source, DISP_NODE, NOT_A));
    cachedMethod = Truffle.getRuntime().createDirectCallNode(methodCallTarget);
    if (VM.instrumentationEnabled()) {
      this.cachedMethod = insert(new InstrumentableDirectCallNode(cachedMethod,
          Tagging.cloneAndUpdateTags(source, VIRTUAL_INVOKE, NOT_A)));
      VM.insertInstrumentationWrapper(cachedMethod);
    }
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    return cachedMethod.call(frame, arguments);
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1;
  }
}
