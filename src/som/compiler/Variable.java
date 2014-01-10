package som.compiler;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.interpreter.nodes.ContextualNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.UninitializedVariableNode.UninitializedSuperReadNode;
import som.interpreter.nodes.UninitializedVariableNode.UninitializedVariableReadNode;
import som.interpreter.nodes.UninitializedVariableNode.UninitializedVariableWriteNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameSlot;

public abstract class Variable {
  public final String name;
  public final FrameSlot slot;

  @CompilationFinal private boolean isRead;
  @CompilationFinal private boolean isReadOutOfContext;

  Variable(final String name, final FrameSlot slot) {
    this.name      = name;
    this.slot      = slot;
    this.isRead    = false;
    this.isReadOutOfContext = false;
  }

  public boolean isAccessed() {
    return isRead;
  }

  public boolean isAccessedOutOfContext() {
    return isReadOutOfContext;
  }

  public final ContextualNode getReadNode(final int contextLevel,
      final FrameSlot localSelf) {
    transferToInterpreterAndInvalidate("Variable.getReadNode");
    isRead = true;
    if (contextLevel > 0) {
      isReadOutOfContext = true;
    }
    return new UninitializedVariableReadNode(this, contextLevel, localSelf);
  }

  public final UninitializedSuperReadNode getSuperReadNode(final int contextLevel,
      final SSymbol holderClass, final boolean classSide,
      final FrameSlot localSelf) {
    isRead = true;
    if (contextLevel > 0) {
      isReadOutOfContext = true;
    }
    return new UninitializedSuperReadNode(this, contextLevel, localSelf,
        holderClass, classSide);
  }

  public static final class Argument extends Variable {
    public final int index;

    Argument(final String name, final FrameSlot slot, final int index) {
      super(name, slot);
      this.index = index;
    }
  }

  public static final class Local extends Variable {
    @CompilationFinal private boolean isWritten;
    @CompilationFinal private boolean isWrittenOutOfContext;

    Local(final String name, final FrameSlot slot) {
      super(name, slot);
      this.isWritten = false;
      this.isWrittenOutOfContext = false;
    }

    @Override
    public boolean isAccessed() {
      return super.isAccessed() || isWritten;
    }

    @Override
    public boolean isAccessedOutOfContext() {
      return super.isAccessedOutOfContext() || isWrittenOutOfContext;
    }

    public ExpressionNode getWriteNode(final int contextLevel,
        final FrameSlot localSelf,
        final ExpressionNode valueExpr) {
      transferToInterpreterAndInvalidate("Variable.getWriteNode");
      isWritten = true;
      if (contextLevel > 0) {
        isWrittenOutOfContext = true;
      }
      return new UninitializedVariableWriteNode(this, contextLevel, localSelf, valueExpr);
    }
  }
}
