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
      final ExpressionNode methodBody, final boolean executesEnforced) {
    super(null, executesEnforced);
    this.argumentInits = argumentInits;
    this.methodBody    = methodBody;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    executeAllArguments(frame);
    return methodBody.executeGeneric(frame);
  }

  @Override
  public void executeVoid(final VirtualFrame frame) {
    executeAllArguments(frame);
    methodBody.executeVoid(frame);
  }

  @ExplodeLoop
  private void executeAllArguments(final VirtualFrame frame) {
    assert frame.getArguments().length == 2 + argumentInits.length;
    for (int i = 0; i < argumentInits.length; i++) {
      argumentInits[i].executeVoid(frame);
    }
  }

  @Override
  public ExpressionNode getFirstMethodBodyNode() {
    return methodBody;
  }
}
