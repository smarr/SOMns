package som.interpreter.nodes.specialized;

import som.interpreter.SArguments;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.PreevaluatedExpression;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vmobjects.SBlock;
import som.vmobjects.SMethod;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.LoopCountReceiver;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.CallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;


public abstract class IntToDoMessageNode extends TernaryExpressionNode
    implements PreevaluatedExpression {

  private final SMethod blockMethod;
  @Child private CallNode valueSend;

  public IntToDoMessageNode(final ExpressionNode orignialNode,
      final SBlock block) {
    super(orignialNode.getSourceSection());
    blockMethod = block.getMethod();
    valueSend = adoptChild(Truffle.getRuntime().createCallNode(
                    blockMethod.getCallTarget()));
  }

  public IntToDoMessageNode(final IntToDoMessageNode node) {
    super(node.getSourceSection());
    this.blockMethod = node.blockMethod;
    this.valueSend   = adoptChild(node.valueSend);
  }

  @Override
  public final Object executePreEvaluated(final VirtualFrame frame,
      final Object receiver, final Object[] arguments) {
    return executeEvaluated(frame, receiver, arguments[0], arguments[1]);
  }

  protected final boolean isSameBlockInt(final int receiver, final int limit, final SBlock block) {
    return block.getMethod() == blockMethod;
  }

  @Specialization(guards = "isSameBlockInt")
  public int doIntToDo(final VirtualFrame frame, final int receiver, final int limit, final SBlock block) {
    try {
      for (int i = receiver; i <= limit; i++) {
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

  protected final boolean isSameBlockDouble(final int receiver, final double limit, final SBlock block) {
    return block.getMethod() == blockMethod;
  }

  @Specialization(guards = "isSameBlockDouble")
  public int doIntToDo(final VirtualFrame frame, final int receiver, final double limit, final SBlock block) {
    try {
      for (int i = receiver; i <= limit; i++) {
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
