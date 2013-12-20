package som.interpreter.nodes;

import som.compiler.Variable.Local;
import som.interpreter.nodes.LocalVariableNode.LocalVariableReadNode;
import som.interpreter.nodes.LocalVariableNode.LocalVariableWriteNode;
import som.interpreter.nodes.LocalVariableNodeFactory.LocalVariableReadNodeFactory;
import som.interpreter.nodes.LocalVariableNodeFactory.LocalVariableWriteNodeFactory;
import som.interpreter.nodes.NonLocalVariableNode.NonLocalVariableReadNode;
import som.interpreter.nodes.NonLocalVariableNode.NonLocalVariableWriteNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class UninitializedVariableNode extends ContextualNode {
  protected final Local variable;

  public UninitializedVariableNode(final Local variable,
      final int contextLevel) {
    super(contextLevel);
    this.variable = variable;
  }

  public static class UninitializedVariableReadNode extends UninitializedVariableNode {
    public UninitializedVariableReadNode(final Local variable, final int contextLevel) {
      super(variable, contextLevel);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      CompilerDirectives.transferToInterpreterAndInvalidate();

      if (variable.isAccessedOutOfContext()) {
        NonLocalVariableReadNode node = new NonLocalVariableReadNode(contextLevel, variable.upvalueIndex);
        return replace(node).executeGeneric(frame);
      } else {
        LocalVariableReadNode node = LocalVariableReadNodeFactory.create(variable);
        return replace(node).executeGeneric(frame);
      }
    }
  }

  public static class UninitializedVariableWriteNode extends UninitializedVariableNode {
    @Child private ExpressionNode exp;

    public UninitializedVariableWriteNode(final Local variable, final int contextLevel, final ExpressionNode exp) {
      super(variable, contextLevel);
      this.exp = adoptChild(exp);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      CompilerDirectives.transferToInterpreterAndInvalidate();

      if (variable.isAccessedOutOfContext()) {
        NonLocalVariableWriteNode node = new NonLocalVariableWriteNode(contextLevel, variable.upvalueIndex, exp);
        return replace(node).executeGeneric(frame);
      } else {
        LocalVariableWriteNode node = LocalVariableWriteNodeFactory.create(variable, exp);
        return replace(node).executeGeneric(frame);
      }
    }
  }
}
