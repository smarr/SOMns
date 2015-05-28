package som.compiler;

import static som.interpreter.SNodeFactory.createArgumentRead;
import static som.interpreter.SNodeFactory.createLocalVarRead;
import static som.interpreter.SNodeFactory.createSuperRead;
import static som.interpreter.SNodeFactory.createVariableWrite;
import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.compiler.ClassBuilder.ClassDefinitionId;
import som.interpreter.nodes.ExpressionNode;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.source.SourceSection;

public abstract class Variable {
  public final String name;

  @CompilationFinal protected boolean isRead;
  @CompilationFinal protected boolean isReadOutOfContext;

  Variable(final String name) {
    this.name      = name;
    this.isRead    = false;
    this.isReadOutOfContext = false;
  }

  @Override
  public String toString() {
    return getClass().getName() + "(" + name + ")";
  }

  public boolean isAccessed() {
    return isRead;
  }

  public boolean isAccessedOutOfContext() {
    return isReadOutOfContext;
  }

  public abstract ExpressionNode getReadNode(final int contextLevel,
      final SourceSection source);

  public final ExpressionNode getSuperReadNode(final int contextLevel,
      final ClassDefinitionId holderClass, final boolean classSide,
      final SourceSection source) {
    isRead = true;
    if (contextLevel > 0) {
      isReadOutOfContext = true;
    }
    return createSuperRead(contextLevel, holderClass, classSide, source);
  }

  public static final class Argument extends Variable {
    public final int index;

    Argument(final String name, final int index) {
      super(name);
      this.index = index;
    }

    public boolean isSelf() {
      return "self".equals(name) || "$blockSelf".equals(name); // TODO: i think, $blockSelf should never be accessed by any program
    }

    @Override
    public ExpressionNode getReadNode(final int contextLevel,
        final SourceSection source) {
      transferToInterpreterAndInvalidate("Variable.getReadNode");
      isRead = true;
      if (contextLevel > 0) {
        isReadOutOfContext = true;
      }
      return createArgumentRead(this, contextLevel, source);
    }
  }

  public static final class Local extends Variable {
    private final FrameSlot slot;
    @CompilationFinal private boolean isWritten;
    @CompilationFinal private boolean isWrittenOutOfContext;

    Local(final String name, final FrameSlot slot) {
      super(name);
      this.isWritten = false;
      this.slot      = slot;
      this.isWrittenOutOfContext = false;
    }

    @Override
    public ExpressionNode getReadNode(final int contextLevel,
        final SourceSection source) {
      transferToInterpreterAndInvalidate("Variable.getReadNode");
      isRead = true;
      if (contextLevel > 0) {
        isReadOutOfContext = true;
      }
      return createLocalVarRead(this, contextLevel, source);
    }

    public FrameSlot getSlot() {
      return slot;
    }

    public Object getSlotIdentifier() {
      return slot.getIdentifier();
    }

    public Local cloneForInlining(final FrameSlot inlinedSlot) {
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
        final ExpressionNode valueExpr, final SourceSection source) {
      transferToInterpreterAndInvalidate("Variable.getWriteNode");
      isWritten = true;
      if (contextLevel > 0) {
        isWrittenOutOfContext = true;
      }
      return createVariableWrite(this, contextLevel, valueExpr, source);
    }
  }
}
