package som.compiler;

import som.interpreter.nodes.ArgumentReadNode;
import som.interpreter.nodes.ContextualNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.UninitializedVariableNode.UninitializedVariableReadNode;
import som.interpreter.nodes.UninitializedVariableNode.UninitializedVariableWriteNode;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameSlot;

public abstract class Variable {

  Variable(final String name) {
    this.name      = name;
    this.isRead    = false;
    this.isWritten = false;
    this.isReadOutOfContext    = false;
    this.isWrittenOutOfContext = false;
  }

  public final String name;

  @CompilationFinal private boolean isRead;
  @CompilationFinal private boolean isWritten;
  @CompilationFinal private boolean isReadOutOfContext;
  @CompilationFinal private boolean isWrittenOutOfContext;

  public void setIsRead() {
    isRead = true;
  }

  public void setIsReadOutOfContext() {
    isReadOutOfContext = true;
  }

  public void setIsWritten() {
    isWritten = true;
  }

  public void setIsWrittenOutOfContext() {
    isWrittenOutOfContext = true;
  }

  public boolean isAccessed() {
    return isRead || isWritten;
  }

  public boolean isAccessedOutOfContext() {
    return isReadOutOfContext || isWrittenOutOfContext;
  }

  public abstract ContextualNode getReadNode(int contextLevel);

  public static final class Argument extends Variable {
    public final int index;

    Argument(final String name, final int index) {
      super(name);
      this.index = index;
    }

    @Override
    public ContextualNode getReadNode(final int contextLevel) {
      return new ArgumentReadNode(this, contextLevel);
    }
  }

  public static final class Local extends Variable {
    public final FrameSlot slot;
    @CompilationFinal public int upvalueIndex;

    Local(final String name, final FrameSlot slot) {
      super(name);
      this.slot = slot;
    }

    @Override
    public ContextualNode getReadNode(final int contextLevel) {
      return new UninitializedVariableReadNode(this, contextLevel);
    }

    public ExpressionNode getWriteNode(final int contextLevel,
        final ExpressionNode valueExpr) {
      return new UninitializedVariableWriteNode(this, contextLevel, valueExpr);
    }
  }
}
