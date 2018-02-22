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
      Object returnValue;
      for (;;) {
        boolean wasOnReturnExecuted = false;
        try {
          probeNode.onEnter(null);
          returnValue = delegateNode.executeDispatch(arguments);
          wasOnReturnExecuted = true;
          probeNode.onReturnValue(null, returnValue);
          return returnValue;
        } catch (Throwable t) {
          // TODO: is passing `null` here as virtual frame an issue?
          Object result = probeNode.onReturnExceptionalOrUnwind(null, t, wasOnReturnExecuted);
          if (result == ProbeNode.UNWIND_ACTION_REENTER) {
            continue;
          } else if (result != null) {
            returnValue = result;
            break;
          } else {
            throw t;
          }
        }
      }
      return returnValue;
    }
  }
}
