package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import som.VmSettings;
import som.instrumentation.DispatchNodeWrapper;


@Instrumentable(factory = DispatchNodeWrapper.class)
public abstract class AbstractDispatchNode
    extends Node implements DispatchChain {
  public static final int INLINE_CACHE_SIZE = VmSettings.DYNAMIC_METRICS ? 100 : 6;

  protected final SourceSection sourceSection;

  protected AbstractDispatchNode(final SourceSection source) {
    super();
    assert source != null;
    this.sourceSection = source;
  }

  /**
   * For wrapped nodes only.
   */
  protected AbstractDispatchNode(final AbstractDispatchNode wrappedNode) {
    super();
    this.sourceSection = null;
  }

  @Override
  public SourceSection getSourceSection() {
    return sourceSection;
  }

  public abstract Object executeDispatch(
      final VirtualFrame frame, final Object[] arguments);
}
