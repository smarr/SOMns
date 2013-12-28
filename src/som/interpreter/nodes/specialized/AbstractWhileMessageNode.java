package som.interpreter.nodes.specialized;

import static som.interpreter.BlockHelper.createInlineableNode;
import som.interpreter.nodes.BinaryMessageNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.UnaryMessageNode;
import som.interpreter.nodes.literals.BlockNode;
import som.vmobjects.SBlock;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.LoopCountReceiver;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;


public abstract class AbstractWhileMessageNode extends BinaryMessageNode {
  @Child protected BlockNode argument;
  @Child protected UnaryMessageNode bodyValueSend;
  protected final SObject predicateBool;

  public AbstractWhileMessageNode(final BinaryMessageNode node,
      final BlockNode argument, final SBlock arg, final SObject predicateBool) {
    super(node);
    this.argument = adoptChild(argument);
    bodyValueSend = adoptChild(createInlineableNode(arg.getMethod(),  universe));
    this.predicateBool = predicateBool;
  }

  @Override public final ExpressionNode getArgument() { return argument; }

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
