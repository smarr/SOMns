package som.interpreter.nodes.specialized;

import som.interpreter.Invokable;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;


public abstract class IntDownToDoMessageNode extends TernaryExpressionNode {

  private final SInvokable blockMethod;
  @Child private DirectCallNode valueSend;

  public IntDownToDoMessageNode(final ExpressionNode orignialNode,
      final SBlock block) {
    super(orignialNode.getSourceSection());
    blockMethod = block.getMethod();
    valueSend = Truffle.getRuntime().createDirectCallNode(
                    blockMethod.getCallTarget());
  }

  public IntDownToDoMessageNode(final IntDownToDoMessageNode node) {
    super(node.getSourceSection());
    this.blockMethod = node.blockMethod;
    this.valueSend   = node.valueSend;
  }

  protected final boolean isSameBlockLong(final SBlock block) {
    return block.getMethod() == blockMethod;
  }

  @Specialization(guards = "isSameBlockLong(block)")
  public final long doIntDownToDo(final VirtualFrame frame, final long receiver, final long limit, final SBlock block) {
    try {
      if (receiver >= limit) {
        valueSend.call(frame, new Object[] {block, receiver});
      }
      for (long i = receiver - 1; i >= limit; i--) {
        valueSend.call(frame, new Object[] {block, i});
      }
    } finally {
      if (CompilerDirectives.inInterpreter() && (receiver - limit) > 0) {
        reportLoopCount(receiver - limit);
      }
    }
    return receiver;
  }

  protected final boolean isSameBlockDouble(final SBlock block) {
    return block.getMethod() == blockMethod;
  }

  @Specialization(guards = "isSameBlockDouble(block)")
  public final long doIntDownToDo(final VirtualFrame frame, final long receiver, final double limit, final SBlock block) {
    try {
      if (receiver >= limit) {
        valueSend.call(frame, new Object[] {block, receiver});
      }
      for (long i = receiver - 1; i >= limit; i--) {
        valueSend.call(frame, new Object[] {block, i});
      }
    } finally {
      if (CompilerDirectives.inInterpreter() && (receiver - (int) limit) > 0) {
        reportLoopCount(receiver - (int) limit);
      }
    }
    return receiver;
  }

  private void reportLoopCount(final long count) {
    CompilerAsserts.neverPartOfCompilation("reportLoopCount");
    Node current = getParent();
    while (current != null && !(current instanceof RootNode)) {
      current = current.getParent();
    }
    if (current != null) {
      ((Invokable) current).propagateLoopCountThroughoutMethodScope(count);
    }
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    return false;
  }
}
