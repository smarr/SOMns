package som.instrumentation;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.source.SourceSection;


public class DirectCallNodeWrapper implements InstrumentableFactory<DirectCallNode> {

  @Override
  public WrapperNode createWrapper(final DirectCallNode node, final ProbeNode probe) {
    return new NodeWrapper(node, probe);
  }

  private static final class NodeWrapper extends DirectCallNode implements WrapperNode {
    @Child private DirectCallNode delegate;
    @Child private ProbeNode      probe;

    private NodeWrapper(final DirectCallNode delegate, final ProbeNode probe) {
      super(null);
      this.delegate = delegate;
      this.probe    = probe;
    }

    @Override
    public Node getDelegateNode() {
      return delegate;
    }

    @Override
    public ProbeNode getProbeNode() {
      return probe;
    }

    @Override
    public NodeCost getCost() {
      return NodeCost.NONE;
    }

    @Override
    public SourceSection getSourceSection() {
      return delegate.getSourceSection();
    }

    @Override
    public Object call(final VirtualFrame frame, final Object[] arguments) {
      try {
        probe.onEnter(frame);
        Object returnValue = delegate.call(frame, arguments);
        probe.onReturnValue(frame, returnValue);
        return returnValue;
      } catch (Throwable t) {
        probe.onReturnExceptional(frame, t);
        throw t;
      }
    }

    @Override
    public boolean isInlinable() {
      return delegate.isInlinable();
    }

    @Override
    public boolean isInliningForced() {
      return delegate.isInliningForced();
    }

    @Override
    public void forceInlining() {
      delegate.forceInlining();
    }

    @Override
    public boolean isCallTargetCloningAllowed() {
      return delegate.isCallTargetCloningAllowed();
    }

    @Override
    public boolean cloneCallTarget() {
      return delegate.cloneCallTarget();
    }

    @Override
    public CallTarget getClonedCallTarget() {
      return delegate.getClonedCallTarget();
    }
  }
}
