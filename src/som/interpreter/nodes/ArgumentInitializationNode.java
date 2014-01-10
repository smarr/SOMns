package som.interpreter.nodes;

import som.interpreter.nodes.LocalVariableNode.LocalVariableWriteNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

/**
 * Initializes the frame slots for self as well as the arguments.
 */
public class ArgumentInitializationNode extends ExpressionNode {
  @Children private final LocalVariableWriteNode[] argumentInits;
  @Child    private       ExpressionNode           methodBody;

  public ArgumentInitializationNode(final LocalVariableWriteNode[] argumentInits,
      final ExpressionNode methodBody) {
    this.argumentInits = adoptChildren(argumentInits);
    this.methodBody    = adoptChild(methodBody);
  }

  @Override
  @ExplodeLoop
  public Object executeGeneric(final VirtualFrame frame) {
    for (int i = 0; i < argumentInits.length; i++) {
      argumentInits[i].executeGeneric(frame);
    }
    return methodBody.executeGeneric(frame);
  }
}
