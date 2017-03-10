package som.instrumentation;

import com.oracle.truffle.api.instrumentation.InstrumentableFactory;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.dispatch.AbstractDispatchNode;


// TODO: figure out why the code is not generated for this
//       lengthOfDispatchChain() seems to be a problem, but looks trivial
public final class DispatchNodeWrapper implements
    InstrumentableFactory<AbstractDispatchNode> {

  @Override
  public WrapperNode createWrapper(final AbstractDispatchNode delegateNode,
      final ProbeNode probeNode) {
    return new DispatchWrapper(delegateNode, probeNode);
  }

  private static final class DispatchWrapper extends AbstractDispatchNode
      implements WrapperNode {

    @Child private AbstractDispatchNode delegateNode;
    @Child private ProbeNode            probeNode;

    private DispatchWrapper(final AbstractDispatchNode delegateNode,
        final ProbeNode probeNode) {
      super(delegateNode);
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
    public SourceSection getSourceSection() {
      return delegateNode.getSourceSection();
    }

    @Override
    public Object executeDispatch(final Object[] arguments) {
      try {
        probeNode.onEnter(null);
        Object returnValue = delegateNode.executeDispatch(arguments);
        probeNode.onReturnValue(null, returnValue);
        return returnValue;
      } catch (Throwable t) {
        probeNode.onReturnExceptional(null, t);
        throw t;
      }
    }
  }
}
