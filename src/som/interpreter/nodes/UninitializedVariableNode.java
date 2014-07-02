package som.interpreter.nodes;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.compiler.Variable;
import som.compiler.Variable.Argument;
import som.compiler.Variable.Local;
import som.interpreter.Inliner;
import som.interpreter.nodes.LocalVariableNode.LocalSuperReadNode;
import som.interpreter.nodes.LocalVariableNode.LocalVariableReadNode;
import som.interpreter.nodes.LocalVariableNode.LocalVariableWriteNode;
import som.interpreter.nodes.LocalVariableNodeFactory.LocalSuperReadNodeFactory;
import som.interpreter.nodes.LocalVariableNodeFactory.LocalVariableReadNodeFactory;
import som.interpreter.nodes.LocalVariableNodeFactory.LocalVariableWriteNodeFactory;
import som.interpreter.nodes.NonLocalVariableNode.NonLocalSuperReadNode;
import som.interpreter.nodes.NonLocalVariableNode.NonLocalVariableReadNode;
import som.interpreter.nodes.NonLocalVariableNode.NonLocalVariableWriteNode;
import som.interpreter.nodes.NonLocalVariableNodeFactory.NonLocalSuperReadNodeFactory;
import som.interpreter.nodes.NonLocalVariableNodeFactory.NonLocalVariableReadNodeFactory;
import som.interpreter.nodes.NonLocalVariableNodeFactory.NonLocalVariableWriteNodeFactory;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class UninitializedVariableNode extends ContextualNode {
  protected final Variable variable;

  public UninitializedVariableNode(final Variable variable,
      final int contextLevel, final FrameSlot localSelf,
      final SourceSection source, final boolean executesEnforced) {
    super(contextLevel, localSelf, source, executesEnforced);
    this.variable = variable;
  }

  public static final class UninitializedVariableReadNode extends UninitializedVariableNode {
    public UninitializedVariableReadNode(final Variable variable,
        final int contextLevel, final FrameSlot localSelf,
        final SourceSection source, final boolean executesEnforced) {
      super(variable, contextLevel, localSelf, source, executesEnforced);
    }

    public UninitializedVariableReadNode(final UninitializedVariableReadNode node,
        final FrameSlot inlinedVarSlot, final FrameSlot inlinedLocalSelfSlot) {
      this(node.variable.cloneForInlining(inlinedVarSlot), node.contextLevel,
          inlinedLocalSelfSlot, node.getSourceSection(), node.executesEnforced);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      transferToInterpreterAndInvalidate("UninitializedVariableReadNode");

      if (contextLevel > 0) {
        NonLocalVariableReadNode node = NonLocalVariableReadNodeFactory.create(
            contextLevel, variable.slot, localSelf, getSourceSection(),
            executesEnforced);
        return replace(node).executeGeneric(frame);
      } else {
        LocalVariableReadNode node = LocalVariableReadNodeFactory.create(
            variable, getSourceSection(), executesEnforced);
        return replace(node).executeGeneric(frame);
      }
    }

    @Override
    public void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }

    @Override
    public void replaceWithIndependentCopyForInlining(final Inliner inliner) {
      FrameSlot localSelfSlot = inliner.getLocalFrameSlot(getLocalSelfSlotIdentifier());
      FrameSlot varSlot       = inliner.getFrameSlot(this, variable.getSlotIdentifier());
      assert localSelfSlot != null;
      assert varSlot       != null;
      replace(new UninitializedVariableReadNode(this, varSlot, localSelfSlot));
    }

    public boolean accessesArgument() {
      return variable instanceof Argument;
    }

    public boolean accessesTemporary() {
      return variable instanceof Local;
    }

    public boolean accessesSelf() {
      if (accessesTemporary()) {
        return false;
      }
      return ((Argument) variable).isSelf();
    }

    public int getArgumentIndex() {
      CompilerAsserts.neverPartOfCompilation();
      if (!accessesArgument()) {
        throw new UnsupportedOperationException("This node does not access an argument.");
      }

      return ((Argument) variable).index;
    }
  }

  public static final class UninitializedSuperReadNode extends UninitializedVariableNode {
    private final SSymbol holderClass;
    private final boolean classSide;

    public UninitializedSuperReadNode(final Variable variable,
        final int contextLevel, final FrameSlot localSelf,
        final SSymbol holderClass, final boolean classSide,
        final SourceSection source, final boolean executesEnforced) {
      super(variable, contextLevel, localSelf, source, executesEnforced);
      this.holderClass = holderClass;
      this.classSide   = classSide;
    }

    public UninitializedSuperReadNode(final UninitializedSuperReadNode node,
        final FrameSlot inlinedVarSlot, final FrameSlot inlinedLocalSelfSlot) {
      this(node.variable.cloneForInlining(inlinedVarSlot), node.contextLevel,
          inlinedLocalSelfSlot, node.holderClass, node.classSide,
          node.getSourceSection(), node.executesEnforced);
    }

    private SClass getLexicalSuperClass() {
      SClass clazz = (SClass) Universe.current().getGlobal(holderClass);
      if (classSide) {
        clazz = clazz.getSOMClass(Universe.current());
      }
      return (SClass) clazz.getSuperClass();
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      transferToInterpreterAndInvalidate("UninitializedSuperReadNode");

      if (accessesOuterContext()) {
        NonLocalSuperReadNode node = NonLocalSuperReadNodeFactory.create(contextLevel,
            variable.slot, localSelf, getLexicalSuperClass(), getSourceSection(), executesEnforced);
        return replace(node).executeGeneric(frame);
      } else {
        LocalSuperReadNode node = LocalSuperReadNodeFactory.create(variable,
            getLexicalSuperClass(), getSourceSection(), executesEnforced);
        return replace(node).
            executeGeneric(frame);
      }
    }

    @Override
    public void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }

    @Override
    public void replaceWithIndependentCopyForInlining(final Inliner inliner) {
      FrameSlot localSelfSlot = inliner.getLocalFrameSlot(getLocalSelfSlotIdentifier());
      FrameSlot varSlot       = inliner.getFrameSlot(this, variable.getSlotIdentifier());
      assert localSelfSlot != null;
      assert varSlot       != null;
      replace(new UninitializedSuperReadNode(this, varSlot, localSelfSlot));
    }
  }

  public static final class UninitializedVariableWriteNode extends UninitializedVariableNode {
    @Child private ExpressionNode exp;

    public UninitializedVariableWriteNode(final Local variable,
        final int contextLevel, final FrameSlot localSelf,
        final ExpressionNode exp, final SourceSection source,
        final boolean executesEnforced) {
      super(variable, contextLevel, localSelf, source, executesEnforced);
      this.exp = exp;
    }

    public UninitializedVariableWriteNode(
        final UninitializedVariableWriteNode node,
        final FrameSlot inlinedVarSlot, final FrameSlot inlinedLocalSelfSlot,
        final boolean executesEnforced) {
      this((Local) node.variable.cloneForInlining(inlinedVarSlot),
          node.contextLevel, inlinedLocalSelfSlot, node.exp,
          node.getSourceSection(), executesEnforced);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      transferToInterpreterAndInvalidate("UninitializedVariableWriteNode");

      if (accessesOuterContext()) {
        NonLocalVariableWriteNode node = NonLocalVariableWriteNodeFactory.create(
            contextLevel, variable.slot, localSelf, getSourceSection(),
            executesEnforced, exp);
        return replace(node).executeGeneric(frame);
      } else {
        LocalVariableWriteNode node = LocalVariableWriteNodeFactory.create(
            (Local) variable, getSourceSection(), executesEnforced, exp);
        return replace(node).executeGeneric(frame);
      }
    }

    @Override
    public void executeVoid(final VirtualFrame frame) {
      transferToInterpreterAndInvalidate("UninitializedVariableWriteNode");

      if (accessesOuterContext()) {
        NonLocalVariableWriteNode node = NonLocalVariableWriteNodeFactory.create(
            contextLevel, variable.slot, localSelf, getSourceSection(),
            executesEnforced, exp);
        replace(node).executeVoid(frame);
      } else {
        LocalVariableWriteNode node = LocalVariableWriteNodeFactory.create(
            (Local) variable, getSourceSection(), executesEnforced, exp);
        replace(node).executeVoid(frame);
      }
    }

    @Override
    public void replaceWithIndependentCopyForInlining(final Inliner inliner) {
      FrameSlot localSelfSlot = inliner.getLocalFrameSlot(getLocalSelfSlotIdentifier());
      FrameSlot varSlot       = inliner.getFrameSlot(this, variable.getSlotIdentifier());
      assert localSelfSlot != null;
      assert varSlot       != null;
      replace(new UninitializedVariableWriteNode(this, varSlot, localSelfSlot,
          executesEnforced));
    }
  }
}
