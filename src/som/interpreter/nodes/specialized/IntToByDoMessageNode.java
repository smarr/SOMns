package som.interpreter.nodes.specialized;

import som.interpreter.SArguments;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.PreevaluatedExpression;
import som.interpreter.nodes.nary.QuaternaryExpressionNode;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.LoopCountReceiver;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.CallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;


public abstract class IntToByDoMessageNode extends QuaternaryExpressionNode
    implements PreevaluatedExpression {

  private final SInvokable blockMethod;
  @Child private CallNode valueSend;

  public IntToByDoMessageNode(final ExpressionNode orignialNode,
      final SBlock block) {
    super(orignialNode.getSourceSection());
    blockMethod = block.getMethod();
    valueSend = Truffle.getRuntime().createCallNode(
                    blockMethod.getCallTarget());
  }

  public IntToByDoMessageNode(final IntToByDoMessageNode node) {
    super(node.getSourceSection());
    this.blockMethod = node.blockMethod;
    this.valueSend   = node.valueSend;
  }

  @Override
  public final Object executePreEvaluated(final VirtualFrame frame,
      final Object receiver, final Object[] arguments) {
    return executeEvaluated(frame, receiver, arguments[0], arguments[1],
        arguments[2]);
  }

  protected final boolean isSameBlockInt(final int receiver, final int limit, final int step, final SBlock block) {
    return block.getMethod() == blockMethod;
  }

  protected final boolean isSameBlockDouble(final int receiver, final double limit, final int step, final SBlock block) {
    return block.getMethod() == blockMethod;
  }

  @Specialization(guards = "isSameBlockInt")
  public final int doIntToByDo(final VirtualFrame frame, final int receiver, final int limit, final int step, final SBlock block) {
    try {
      for (int i = receiver; i <= limit; i += step) {
        SArguments arguments = new SArguments(block, new Object[] {i});
        valueSend.call(frame.pack(), arguments);
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        reportLoopCount(limit - receiver);
      }
    }
    return receiver;
  }

  @Specialization(guards = "isSameBlockDouble")
  public final int doIntToByDo(final VirtualFrame frame, final int receiver, final double limit, final int step, final SBlock block) {
    try {
      for (int i = receiver; i <= limit; i += step) {
        SArguments arguments = new SArguments(block, new Object[] {i});
        valueSend.call(frame.pack(), arguments);
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        reportLoopCount((int) limit - receiver);
      }
    }
    return receiver;
  }

  protected final void reportLoopCount(final int count) {
    CompilerAsserts.neverPartOfCompilation();
    Node current = getParent();
    while (current != null && !(current instanceof RootNode)) {
      current = current.getParent();
    }
    if (current != null) {
      RootNode root = (RootNode) current;
      if (root.getCallTarget() instanceof LoopCountReceiver) {
        ((LoopCountReceiver) root.getCallTarget()).reportLoopCount(count);
      }
    }
  }
}
