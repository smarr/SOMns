package dym.nodes;

import som.instrumentation.InstrumentableDirectCallNode;
import som.interpreter.Invokable;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;

import dym.profiles.CallsiteProfile;


public class LateCallTargetNode extends ExecutionEventNode {
  private final EventContext ctx;
  private final ExecutionEventNodeFactory factory;

  public LateCallTargetNode(final EventContext ctx, final ExecutionEventNodeFactory factory) {
    this.ctx = ctx;
    this.factory = factory;
  }

  private ExecutionEventNode specialize() {
    ExecutionEventNode parent = ctx.findParentEventNode(factory);
    InstrumentableDirectCallNode disp = (InstrumentableDirectCallNode) ctx.getInstrumentedNode();

    if (parent == null) {
      return this;
    }

    @SuppressWarnings("unchecked")
    CountingNode<CallsiteProfile> p = (CountingNode<CallsiteProfile>) parent;
    CallsiteProfile profile = p.getProfile();
    RootCallTarget root = (RootCallTarget) disp.getCallTarget();
    return replace(new CallTargetNode(profile, (Invokable) root.getRootNode()));
  }

  @Override
  protected void onEnter(final VirtualFrame frame) {
    ExecutionEventNode node = specialize();
    if (node == this) {
      return;
    } else {
      ((CallTargetNode) node).onEnter(frame);
    }
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    ExecutionEventNode node = specialize();
    if (node == this) {
      return;
    } else {
      ((CallTargetNode) node).onReturnValue(frame, result);
    }
  }

  @Override
  protected void onReturnExceptional(final VirtualFrame frame, final Throwable exception) {
    ExecutionEventNode node = specialize();
    if (node == this) {
      return;
    } else {
      ((CallTargetNode) node).onReturnExceptional(frame, exception);
    }
  }
}
