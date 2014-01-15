package som.interpreter.nodes.specialized;

import static som.interpreter.BlockHelper.createBinaryInlineableNode;
import som.interpreter.nodes.BinaryMessageNode;
import som.interpreter.nodes.TernaryMessageNode;
import som.vmobjects.SBlock;
import som.vmobjects.SMethod;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.LoopCountReceiver;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;


public abstract class IntToDoMessageNode extends TernaryMessageNode {
//  @Child protected ExpressionNode receiver;
//  @Child protected ExpressionNode firstArg;
//  @Child protected BlockNode      secondArg;

  private final SMethod blockMethod;
  @Child protected BinaryMessageNode valueSend;

  public IntToDoMessageNode(final TernaryMessageNode node, final SBlock block) {
    super(node);
    blockMethod = block.getMethod();
    valueSend   = adoptChild(createBinaryInlineableNode(blockMethod, universe));
  }

  public IntToDoMessageNode(final IntToDoMessageNode node) {
    super(node);
    this.blockMethod = node.blockMethod;
    this.valueSend   = node.valueSend;
  }

  @Specialization
  public int doIntToDo(final VirtualFrame frame, final int receiver, final int limit, final SBlock block) {
    try {
      for (int i = receiver; i <= limit; i++) {
        valueSend.executeEvaluated(frame, universe.newBlock(block), i);
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        reportLoopCount(limit - receiver);
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
