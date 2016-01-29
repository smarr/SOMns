package som.instrumentation;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.interpreter.nodes.PreevaluatedExpression;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;


public class MessageSendNodeWrapper
    implements InstrumentableFactory<AbstractMessageSendNode> {

  @Override
  public WrapperNode createWrapper(
      final AbstractMessageSendNode node, final ProbeNode probe) {
    return new MessageSendWrapper(node, probe);
  }

  private static final class MessageSendWrapper
    extends AbstractMessageSendNode implements WrapperNode {

    @Child private ExpressionNode delegate;
    @Child private ProbeNode      probe;

    private MessageSendWrapper(final AbstractMessageSendNode delegate,
        final ProbeNode probe) {
      super(null, delegate.getSourceSection());
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
    public Object doPreEvaluated(final VirtualFrame frame, final Object[] args) {
      try {
        probe.onEnter(frame);
        Object result = ((PreevaluatedExpression) delegate).doPreEvaluated(frame, args);
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
