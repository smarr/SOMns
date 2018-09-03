package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import som.vm.VmSettings;


@GenerateWrapper
public abstract class AbstractDispatchNode extends Node
    implements DispatchChain, InstrumentableNode {
  public static final int INLINE_CACHE_SIZE = VmSettings.DYNAMIC_METRICS ? 100 : 6;

  protected final SourceSection sourceSection;

  protected AbstractDispatchNode(final SourceSection source) {
    this.sourceSection = source;
  }

  /**
   * For wrapped nodes only.
   */
  protected AbstractDispatchNode() {
    this.sourceSection = null;
  }

  @Override
  public SourceSection getSourceSection() {
    return sourceSection;
  }

  public abstract Object executeDispatch(VirtualFrame frame, Object[] arguments);

  @Override
  public boolean isInstrumentable() {
    return true;
  }

  @Override
  public WrapperNode createWrapper(final ProbeNode probe) {
    return new AbstractDispatchNodeWrapper(this, probe);
  }
}
