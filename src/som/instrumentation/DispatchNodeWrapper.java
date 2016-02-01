package som.instrumentation;

import som.interpreter.nodes.dispatch.AbstractDispatchNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.source.SourceSection;


// TODO: figure out why the code is not generated for this
//       lengthOfDispatchChain() seems to be a problem, but looks trivial
public final class DispatchNodeWrapper implements
    InstrumentableFactory<AbstractDispatchNode> {

  @Override
  public WrapperNode createWrapper(final AbstractDispatchNode delegateNode,
      final ProbeNode probeNode) {
    return new DispatchWrapper(delegateNode.getSourceSection(),
        delegateNode, probeNode);
  }

  private static final class DispatchWrapper extends AbstractDispatchNode
      implements WrapperNode {

    @Child private AbstractDispatchNode delegateNode;
    @Child private ProbeNode            probeNode;

    private DispatchWrapper(final SourceSection source,
        final AbstractDispatchNode delegateNode, final ProbeNode probeNode) {
      super(source);
      this.delegateNode = delegateNode;
      this.probeNode = probeNode;
    }

    @Override
    public AbstractDispatchNode getDelegateNode() {
      return delegateNode;
    }

    @Override
    public ProbeNode getProbeNode() {
      return probeNode;
    }

    @Override
    public NodeCost getCost() {
      return NodeCost.NONE;
    }

    @Override
    public int lengthOfDispatchChain() {
      return delegateNode.lengthOfDispatchChain();
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
      try {
        probeNode.onEnter(frame);
        Object returnValue = delegateNode.executeDispatch(frame, arguments);
        probeNode.onReturnValue(frame, returnValue);
        return returnValue;
      } catch (Throwable t) {
        probeNode.onReturnExceptional(frame, t);
        throw t;
      }
    }
  }
}
