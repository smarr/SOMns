package som.interpreter.nodes.dispatch;

import som.instrumentation.DispatchNodeWrapper;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;


@Instrumentable(factory = DispatchNodeWrapper.class)
public abstract class AbstractDispatchNode
    extends Node implements DispatchChain {
  public static final int INLINE_CACHE_SIZE = 6;

  private final SourceSection sourceSection;

  protected AbstractDispatchNode(final SourceSection source) {
    super();
    assert source != null;
    this.sourceSection = source;
  }

  @Override
  public final SourceSection getSourceSection() {
    return sourceSection;
  }

  public abstract Object executeDispatch(
      final VirtualFrame frame, final Object[] arguments);
}
