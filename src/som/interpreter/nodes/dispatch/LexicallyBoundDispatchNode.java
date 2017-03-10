package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.instrumentation.InstrumentableDirectCallNode;
import som.vm.VmSettings;


/**
 * Private methods are special, they are linked unconditionally to the call site.
 * Thus, we don't need to check at the dispatch whether they apply or not.
 */
public final class LexicallyBoundDispatchNode extends AbstractDispatchNode {

  @Child private DirectCallNode cachedMethod;

  public LexicallyBoundDispatchNode(final SourceSection source, final CallTarget methodCallTarget) {
    super(source);
    cachedMethod = Truffle.getRuntime().createDirectCallNode(methodCallTarget);
    if (VmSettings.DYNAMIC_METRICS) {
      this.cachedMethod = insert(
          new InstrumentableDirectCallNode(cachedMethod, source));
      VM.insertInstrumentationWrapper(cachedMethod);
    }
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    return cachedMethod.call(arguments);
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1;
  }
}
