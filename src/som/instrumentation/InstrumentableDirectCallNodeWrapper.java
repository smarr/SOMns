package som.instrumentation;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.NodeCost;


public final class InstrumentableDirectCallNodeWrapper extends InstrumentableDirectCallNode
    implements WrapperNode {
  @Child private InstrumentableDirectCallNode delegateNode;
  @Child private ProbeNode                    probeNode;

  InstrumentableDirectCallNodeWrapper(final InstrumentableDirectCallNode delegateNode,
      final ProbeNode probeNode) {
    this.delegateNode = delegateNode;
    this.probeNode = probeNode;
  }

  @Override
  public InstrumentableDirectCallNode getDelegateNode() {
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
  public Object call(final Object[] arguments) {
    return delegateNode.call(arguments);
  }

  @Override
  public CallTarget getCallTarget() {
    return delegateNode.getCallTarget();
  }

  @Override
  public boolean isInlinable() {
    return delegateNode.isInlinable();
  }

  @Override
  public boolean isInliningForced() {
    return delegateNode.isInliningForced();
  }

  @Override
  public void forceInlining() {
    delegateNode.forceInlining();
  }

  @Override
  public boolean isCallTargetCloningAllowed() {
    return delegateNode.isCallTargetCloningAllowed();
  }

  @Override
  public boolean cloneCallTarget() {
    return delegateNode.cloneCallTarget();
  }

  @Override
  public CallTarget getClonedCallTarget() {
    return delegateNode.getClonedCallTarget();
  }
}
