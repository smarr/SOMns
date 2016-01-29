package som.instrumentation;

import som.interpreter.nodes.nary.BinaryExpressionNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;


public class BinaryNodeWrapper
    implements InstrumentableFactory<BinaryExpressionNode>{

  @Override
  public InstrumentableFactory.WrapperNode createWrapper(
      final BinaryExpressionNode node, final ProbeNode probe) {
    return new BinaryWrapper(node, probe);
  }

  private static final class BinaryWrapper
    extends BinaryExpressionNode implements WrapperNode {

    @Child private BinaryExpressionNode delegate;
    @Child private ProbeNode            probe;

    private BinaryWrapper(final BinaryExpressionNode delegate,
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
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver, final Object arg) {
      try {
        probe.onEnter(frame);
        Object result = delegate.executeEvaluated(frame, receiver, arg);
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
