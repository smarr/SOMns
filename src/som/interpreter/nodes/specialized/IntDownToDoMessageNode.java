package som.interpreter.nodes.specialized;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import tools.dym.Tags.LoopNode;


public abstract class IntDownToDoMessageNode extends TernaryExpressionNode {

  private final SInvokable blockMethod;
  @Child private DirectCallNode valueSend;

  public IntDownToDoMessageNode(final ExpressionNode orignialNode,
      final SBlock block) {
    super(false, orignialNode.getSourceSection());
    blockMethod = block.getMethod();
    valueSend = Truffle.getRuntime().createDirectCallNode(
                    blockMethod.getCallTarget());
  }

  public IntDownToDoMessageNode(final IntDownToDoMessageNode node) {
    super(false, node.getSourceSection());
    this.blockMethod = node.blockMethod;
    this.valueSend   = node.valueSend;
  }

  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == LoopNode.class) {
      return true;
    } else {
      return super.isTaggedWithIgnoringEagerness(tag);
    }
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
        SomLoop.reportLoopCount(receiver - limit, this);
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
        SomLoop.reportLoopCount(receiver - (int) limit, this);
      }
    }
    return receiver;
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    return false;
  }
}
