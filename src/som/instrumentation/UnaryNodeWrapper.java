package som.instrumentation;

import som.interpreter.nodes.nary.UnaryExpressionNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;


public class UnaryNodeWrapper
    implements InstrumentableFactory<UnaryExpressionNode>{

  @Override
  public InstrumentableFactory.WrapperNode createWrapper(
      final UnaryExpressionNode node, final ProbeNode probe) {
    return new UnaryWrapper(node, probe);
  }

  private static final class UnaryWrapper
    extends UnaryExpressionNode implements WrapperNode {

    @Child private UnaryExpressionNode delegate;
    @Child private ProbeNode           probe;

    private UnaryWrapper(final UnaryExpressionNode delegate,
        final ProbeNode probe) {
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
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver) {
      try {
        probe.onEnter(frame);
        Object result = delegate.executeEvaluated(frame, receiver);
        probe.onReturnValue(frame, result);
        return result;
      } catch (Throwable t) {
        probe.onReturnExceptional(frame, t);
        throw t;
      }
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      try {
        probe.onEnter(frame);
        Object result = delegate.executeGeneric(frame);
        probe.onReturnValue(frame, result);
        return result;
      } catch (Throwable t) {
        probe.onReturnExceptional(frame, t);
        throw t;
      }
    }
  }
}
