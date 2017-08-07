package tools.dym.nodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;

import som.instrumentation.InstrumentableDirectCallNode.InstrumentableBlockApplyNode;
import som.interpreter.Invokable;
import tools.dym.profiles.ClosureApplicationProfile;


public class LateClosureTargetNode extends ExecutionEventNode {
  private final EventContext              ctx;
  private final ExecutionEventNodeFactory factory;

  public LateClosureTargetNode(final EventContext ctx,
      final ExecutionEventNodeFactory factory) {
    this.ctx = ctx;
    this.factory = factory;
  }

  @TruffleBoundary
  private ExecutionEventNode specialize() {
    ExecutionEventNode parent = ctx.findParentEventNode(factory);
    InstrumentableBlockApplyNode disp =
        (InstrumentableBlockApplyNode) ctx.getInstrumentedNode();

    if (parent == null) {
      return this;
    }

    @SuppressWarnings("unchecked")
    CountingNode<ClosureApplicationProfile> p =
        (CountingNode<ClosureApplicationProfile>) parent;
    ClosureApplicationProfile profile = p.getProfile();
    RootCallTarget root = (RootCallTarget) disp.getCallTarget();
    return replace(new ClosureTargetNode(profile, (Invokable) root.getRootNode()));
  }

  @Override
  protected void onEnter(final VirtualFrame frame) {
    ExecutionEventNode node = specialize();
    if (node == this) {
      return;
    } else {
      ((ClosureTargetNode) node).onEnter(frame);
    }
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    ExecutionEventNode node = specialize();
    if (node == this) {
      return;
    } else {
      ((ClosureTargetNode) node).onReturnValue(frame, result);
    }
  }

  @Override
  protected void onReturnExceptional(final VirtualFrame frame, final Throwable exception) {
    ExecutionEventNode node = specialize();
    if (node == this) {
      return;
    } else {
      ((ClosureTargetNode) node).onReturnExceptional(frame, exception);
    }
  }
}
