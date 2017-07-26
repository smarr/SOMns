package som.compiler;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.interpreter.nodes.ArgumentReadNode.LocalArgumentReadNode;
import som.interpreter.nodes.ArgumentReadNode.LocalSelfReadNode;
import som.interpreter.nodes.ArgumentReadNode.LocalSuperReadNode;
import som.interpreter.nodes.ArgumentReadNode.NonLocalArgumentReadNode;
import som.interpreter.nodes.ArgumentReadNode.NonLocalSelfReadNode;
import som.interpreter.nodes.ArgumentReadNode.NonLocalSuperReadNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.LocalVariableNodeFactory.LocalVariableReadNodeGen;
import som.interpreter.nodes.LocalVariableNodeFactory.LocalVariableWriteNodeGen;
import som.interpreter.nodes.NonLocalVariableNodeFactory.NonLocalVariableReadNodeGen;
import som.interpreter.nodes.NonLocalVariableNodeFactory.NonLocalVariableWriteNodeGen;
import tools.SourceCoordinate;


/**
 * Represents state belonging to a method activation.
 */
public abstract class Variable {
  public final String name;
  public final SourceSection source;

  Variable(final String name, final SourceSection source) {
    this.name   = name;
    this.source = source;
  }

  /** Gets the name including lexical location. */
  public String getQualifiedName() {
    return name + SourceCoordinate.getLocationQualifier(source);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + name + ")";
  }

  @Override
  public boolean equals(final Object o) {
    assert o != null;
    if (o == this) { return true; }
    if (!(o instanceof Variable)) { return false; }
    Variable var = (Variable) o;
    if (var.source == source) {
      assert name.equals(var.name) : "Defined in the same place, but names not equal?";
      return true;
    }
    assert source == null || !source.equals(var.source) : "Why are there multiple objects for this source section? might need to fix comparison above";
    return false;
  }

  public abstract ExpressionNode getReadNode(int contextLevel, SourceSection source);

  public abstract Variable split(FrameDescriptor descriptor);
  public abstract Local splitToMergeIntoOuterScope(FrameDescriptor descriptor);

  /** Access method for the debugger and tools. Not to be used in language. */
  public abstract Object read(Frame frame);

  /** Not meant to be shown in debugger or other tools. */
  public boolean isInternal() { return false; }

  /**
   * Represents a parameter (argument) to a method activation.
   * Arguments are stored in the arguments array in a {@link Frame}.
   */
  public static final class Argument extends Variable {
    public final int index;

    Argument(final String name, final int index, final SourceSection source) {
      super(name, source);
      this.index = index;
    }

    public boolean isSelf() {
      return "self".equals(name) || "$blockSelf".equals(name);
    }

    public ExpressionNode getSelfReadNode(final int contextLevel,
        final MixinDefinitionId holderMixin,
        final SourceSection source) {
      assert this instanceof Argument;
      if (contextLevel == 0) {
        return new LocalSelfReadNode(this, holderMixin, source);
      } else {
        return new NonLocalSelfReadNode(this, holderMixin, contextLevel, source);
      }
    }

    public ExpressionNode getSuperReadNode(final int contextLevel,
        final MixinDefinitionId holderClass, final boolean classSide,
        final SourceSection source) {
      assert this instanceof Argument;
      if (contextLevel == 0) {
        return new LocalSuperReadNode(this, holderClass, classSide, source);
      } else {
        return new NonLocalSuperReadNode(this, contextLevel, holderClass, classSide, source);
      }
    }

    @Override
    public Variable split(final FrameDescriptor descriptor) {
      return this;
    }

    @Override
    public Local splitToMergeIntoOuterScope(final FrameDescriptor descriptor) {
      if (isSelf()) {
        return null;
      }

      Local l = new ImmutableLocal(name, source);
      l.init(descriptor.addFrameSlot(l));
      return l;
    }

    @Override
    public ExpressionNode getReadNode(final int contextLevel,
        final SourceSection source) {
      transferToInterpreterAndInvalidate("Variable.getReadNode");
      if (contextLevel == 0) {
        return new LocalArgumentReadNode(this, source);
      } else {
        return new NonLocalArgumentReadNode(this, contextLevel, source);
      }
    }

    @Override
    public Object read(final Frame frame) {
      VM.callerNeedsToBeOptimized("Not to be used outside of tools");
      return frame.getArguments()[index];
    }
  }

  /**
   * Represents a local variable, i.e., local to a specific scope.
   * Locals are stored in {@link FrameSlot}s inside a {@link Frame}.
   */
  public abstract static class Local extends Variable {
    @CompilationFinal private FrameSlot slot;

    Local(final String name, final SourceSection source) {
      super(name, source);
    }

    public void init(final FrameSlot slot) {
      this.slot = slot;
    }

    @Override
    public ExpressionNode getReadNode(final int contextLevel,
        final SourceSection source) {
      transferToInterpreterAndInvalidate("Variable.getReadNode");
      if (contextLevel == 0) {
        return LocalVariableReadNodeGen.create(this, source);
      } else {
        return NonLocalVariableReadNodeGen.create(contextLevel, this, source);
      }
    }

    public FrameSlot getSlot() {
      return slot;
    }

    protected abstract Local create();

    @Override
    public Local split(final FrameDescriptor descriptor) {
      Local newLocal = create();
      newLocal.init(descriptor.addFrameSlot(newLocal));
      return newLocal;
    }

    public ExpressionNode getWriteNode(final int contextLevel,
        final ExpressionNode valueExpr, final SourceSection source) {
      transferToInterpreterAndInvalidate("Variable.getWriteNode");
      if (contextLevel == 0) {
        return LocalVariableWriteNodeGen.create(this, source, valueExpr);
      } else {
        return NonLocalVariableWriteNodeGen.create(
            contextLevel, this, source, valueExpr);
      }
    }

    @Override
    public Local splitToMergeIntoOuterScope(final FrameDescriptor descriptor) {
      return split(descriptor);
    }

    @Override
    public Object read(final Frame frame) {
      VM.callerNeedsToBeOptimized("Not to be used outside of tools");
      return frame.getValue(slot);
    }
  }

  public static final class MutableLocal extends Local {
    MutableLocal(final String name, final SourceSection source) {
      super(name, source);
    }

    @Override
    public Local create() {
      return new MutableLocal(name, source);
    }
  }

  public static final class ImmutableLocal extends Local {
    ImmutableLocal(final String name, final SourceSection source) {
      super(name, source);
    }

    @Override
    public Local create() {
      return new ImmutableLocal(name, source);
    }
  }

  /**
   * Represents a variable that is used for internal purposes only.
   * Does not hold language-level values, and thus, is ignored in the debugger.
   * `Internals` are stored in {@link FrameSlot}s.
   */
  public static final class Internal extends Variable {
    @CompilationFinal private FrameSlot slot;

    public Internal(final String name) {
      super(name, null);
    }

    public void init(final FrameSlot slot) {
      this.slot = slot;
    }

    public FrameSlot getSlot() {
      return slot;
    }

    @Override
    public Variable split(final FrameDescriptor descriptor) {
      Internal newInternal = new Internal(name);
      assert slot.getKind() == FrameSlotKind.Object : "We only have the on stack marker currently, so, we expect those not to specialize";
      newInternal.init(descriptor.addFrameSlot(newInternal, slot.getKind()));
      return newInternal;
    }

    @Override
    public ExpressionNode getReadNode(final int contextLevel,
        final SourceSection source) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Local splitToMergeIntoOuterScope(final FrameDescriptor descriptor) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object read(final Frame frame) {
      throw new UnsupportedOperationException("This is for reading language-level values. Think, we should not need this");
    }

    @Override
    public boolean isInternal() { return true; }
  }
}
