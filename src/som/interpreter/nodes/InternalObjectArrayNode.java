package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

import som.interpreter.SArguments;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.vm.VmSettings;
import tools.asyncstacktraces.ShadowStackEntryLoad;
import tools.asyncstacktraces.UninitializedShadowStackEntryLoad;


@NodeInfo(cost = NodeCost.NONE)
public class InternalObjectArrayNode extends ExprWithTagsNode {
  @Children protected final ExpressionNode[] expressions;
  @Child protected ShadowStackEntryLoad      shadowStackEntryLoad =
      VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE ? new UninitializedShadowStackEntryLoad()
          : null;

  public InternalObjectArrayNode(final ExpressionNode[] expressions) {
    this.expressions = expressions;
  }

  @Override
  @ExplodeLoop
  public Object[] executeObjectArray(final VirtualFrame frame) {
    Object[] values = new Object[expressions.length];
    for (int i = 0; i < expressions.length; i++) {
      values[i] = expressions[i].executeGeneric(frame);
    }
    return values;
  }

  @Override
  public final Object executeGeneric(final VirtualFrame frame) {
    return executeObjectArray(frame);
  }

  public static class ArgumentEvaluationNode
      extends InternalObjectArrayNode {

    public ArgumentEvaluationNode(final ExpressionNode[] expressions) {
      super(expressions);
    }

    @Override
    @ExplodeLoop
    public Object[] executeObjectArray(final VirtualFrame frame) {
      Object[] values = SArguments.allocateArgumentsArray(expressions);
      for (int i = 0; i < expressions.length; i++) {
        values[i] = expressions[i].executeGeneric(frame);
      }
      SArguments.setShadowStackEntryWithCache(values, this, shadowStackEntryLoad, frame, true);
      return values;
    }
  }
}
