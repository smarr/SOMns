package som.interpreter.nodes;

import som.interpreter.nodes.LocalVariableNode.LocalVariableWriteNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

/**
 * Initializes the frame slots for self as well as the arguments.
 */
public final class ArgumentInitializationNode extends ExpressionNode {
  @Children private final LocalVariableWriteNode[] argumentInits;
  @Child    private       ExpressionNode           methodBody;

  public ArgumentInitializationNode(
      final LocalVariableWriteNode[] argumentInits,
      final ExpressionNode methodBody) {
    super(null);
    this.argumentInits = argumentInits;
    this.methodBody    = methodBody;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    executeAllArguments(frame);
    return methodBody.executeGeneric(frame);
  }

  @ExplodeLoop
  private void executeAllArguments(final VirtualFrame frame) {
    for (int i = 0; i < argumentInits.length; i++) {
      argumentInits[i].executeGeneric(frame);
    }
  }

  @Override
  public ExpressionNode getFirstMethodBodyNode() {
    return methodBody;
  }
}
