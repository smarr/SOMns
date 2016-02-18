package som.compiler;

import static som.interpreter.SNodeFactory.createArgumentRead;
import static som.interpreter.SNodeFactory.createLocalVarRead;
import static som.interpreter.SNodeFactory.createSelfRead;
import static som.interpreter.SNodeFactory.createSuperRead;
import static som.interpreter.SNodeFactory.createVariableWrite;
import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.interpreter.nodes.ExpressionNode;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.source.SourceSection;

public abstract class Variable {
  public final String name;
  public final SourceSection source;

  Variable(final String name, final SourceSection source) {
    this.name   = name;
    this.source = source;
  }

  @Override
  public String toString() {
    return getClass().getName() + "(" + name + ")";
  }

  public abstract ExpressionNode getReadNode(final int contextLevel,
      final SourceSection source);

  public final ExpressionNode getSelfReadNode(final int contextLevel,
      final MixinDefinitionId holderMixin,
      final SourceSection source) {
    assert this instanceof Argument;
    return createSelfRead(contextLevel, holderMixin, source);
  }

  public final ExpressionNode getSuperReadNode(final int contextLevel,
      final MixinDefinitionId holderClass, final boolean classSide,
      final SourceSection source) {
    assert this instanceof Argument;
    return createSuperRead(contextLevel, holderClass, classSide, source);
  }

  public static final class Argument extends Variable {
    public final int index;

    Argument(final String name, final int index, final SourceSection source) {
      super(name, source);
      this.index = index;
    }

    public boolean isSelf() {
      return "self".equals(name);
    }

    @Override
    public ExpressionNode getReadNode(final int contextLevel,
        final SourceSection source) {
      transferToInterpreterAndInvalidate("Variable.getReadNode");
      return createArgumentRead(this, contextLevel, source.withTags(Tags.LOCAL_ARG_READ));
    }
  }

  public static final class Local extends Variable {
    private final FrameSlot slot;

    Local(final String name, final FrameSlot slot, final SourceSection source) {
      super(name, source);
      this.slot = slot;
    }

    @Override
    public ExpressionNode getReadNode(final int contextLevel,
        final SourceSection source) {
      transferToInterpreterAndInvalidate("Variable.getReadNode");
      return createLocalVarRead(this, contextLevel, source.withTags(Tags.LOCAL_VAR_READ));
    }

    public FrameSlot getSlot() {
      return slot;
    }

    public Object getSlotIdentifier() {
      return slot.getIdentifier();
    }

    public Local cloneForInlining(final FrameSlot inlinedSlot) {
      Local local = new Local(name, inlinedSlot, source);
      return local;
    }

    public ExpressionNode getWriteNode(final int contextLevel,
        final ExpressionNode valueExpr, final SourceSection source) {
      transferToInterpreterAndInvalidate("Variable.getWriteNode");
      return createVariableWrite(this, contextLevel, valueExpr, source.withTags(Tags.LOCAL_VAR_WRITE));
    }
  }
}
