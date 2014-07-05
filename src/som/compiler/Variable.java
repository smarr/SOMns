package som.compiler;

import static som.interpreter.SNodeFactory.createSuperRead;
import static som.interpreter.SNodeFactory.createVariableRead;
import static som.interpreter.SNodeFactory.createVariableWrite;
import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.interpreter.nodes.ContextualNode;
import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.FrameSlot;

public abstract class Variable {
  public final String name;
  public final FrameSlot slot;

  @CompilationFinal protected boolean isRead;
  @CompilationFinal protected boolean isReadOutOfContext;

  Variable(final String name, final FrameSlot slot) {
    this.name      = name;
    this.slot      = slot;
    this.isRead    = false;
    this.isReadOutOfContext = false;
  }

  @Override
  public String toString() {
    return getClass().getName() + "(" + name + ")";
  }

  public final FrameSlot getSlot() {
    return slot;
  }

  public final Object getSlotIdentifier() {
    return slot.getIdentifier();
  }

  public abstract Variable cloneForInlining(final FrameSlot inlinedSlot);

  public boolean isAccessed() {
    return isRead;
  }

  public boolean isAccessedOutOfContext() {
    return isReadOutOfContext;
  }

  public final ContextualNode getReadNode(final int contextLevel,
      final FrameSlot localSelf, final SourceSection source,
      final boolean executeEnforced) {
    transferToInterpreterAndInvalidate("Variable.getReadNode");
    isRead = true;
    if (contextLevel > 0) {
      isReadOutOfContext = true;
    }
    return createVariableRead(this, contextLevel, localSelf, source, executeEnforced);
  }

  public final ContextualNode getSuperReadNode(final int contextLevel,
      final SSymbol holderClass, final boolean classSide,
      final FrameSlot localSelf, final SourceSection source, final boolean executeEnforced) {
    isRead = true;
    if (contextLevel > 0) {
      isReadOutOfContext = true;
    }
    return createSuperRead(this, contextLevel, localSelf, holderClass, classSide, source, executeEnforced);
  }

  public static final class Argument extends Variable {
    public final int index;

    Argument(final String name, final FrameSlot slot, final int index) {
      super(name, slot);
      this.index = index;
    }

    @Override
    public Variable cloneForInlining(final FrameSlot inlinedSlot) {
      Argument arg = new Argument(name, inlinedSlot, index);
      arg.isRead = isRead;
      arg.isReadOutOfContext = isReadOutOfContext;
      return arg;
    }

    public boolean isSelf() {
      return "self".equals(name) || "$blockSelf".equals(name);
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
    public Variable cloneForInlining(final FrameSlot inlinedSlot) {
      Local local = new Local(name, inlinedSlot);
      local.isRead = isRead;
      local.isReadOutOfContext = isReadOutOfContext;
      local.isWritten = isWritten;
      local.isWrittenOutOfContext = isWrittenOutOfContext;
      return local;
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
        final FrameSlot localSelf, final ExpressionNode valueExpr,
        final SourceSection source, final boolean executeEnforced) {
      transferToInterpreterAndInvalidate("Variable.getWriteNode");
      isWritten = true;
      if (contextLevel > 0) {
        isWrittenOutOfContext = true;
      }
      return createVariableWrite(this, contextLevel, localSelf, valueExpr,
          source, executeEnforced);
    }
  }
}
