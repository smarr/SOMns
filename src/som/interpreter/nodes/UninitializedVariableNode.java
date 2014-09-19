package som.interpreter.nodes;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.compiler.Variable.Local;
import som.interpreter.Inliner;
import som.interpreter.nodes.ArgumentReadNode.LocalSuperReadNode;
import som.interpreter.nodes.ArgumentReadNode.NonLocalSuperReadNode;
import som.interpreter.nodes.LocalVariableNode.LocalVariableReadNode;
import som.interpreter.nodes.LocalVariableNode.LocalVariableWriteNode;
import som.interpreter.nodes.LocalVariableNodeFactory.LocalVariableReadNodeFactory;
import som.interpreter.nodes.LocalVariableNodeFactory.LocalVariableWriteNodeFactory;
import som.interpreter.nodes.NonLocalVariableNode.NonLocalVariableReadNode;
import som.interpreter.nodes.NonLocalVariableNode.NonLocalVariableWriteNode;
import som.interpreter.nodes.NonLocalVariableNodeFactory.NonLocalVariableReadNodeFactory;
import som.interpreter.nodes.NonLocalVariableNodeFactory.NonLocalVariableWriteNodeFactory;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;


public abstract class UninitializedVariableNode extends ContextualNode {
  protected final Local variable;

  public UninitializedVariableNode(final Local variable,
      final int contextLevel, final SourceSection source) {
    super(contextLevel, source);
    this.variable = variable;
  }

  public static final class UninitializedVariableReadNode extends UninitializedVariableNode {
    public UninitializedVariableReadNode(final Local variable,
        final int contextLevel, final SourceSection source) {
      super(variable, contextLevel, source);
    }

    public UninitializedVariableReadNode(final UninitializedVariableReadNode node,
        final FrameSlot inlinedVarSlot) {
      this(node.variable.cloneForInlining(inlinedVarSlot), node.contextLevel,
          node.getSourceSection());
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      transferToInterpreterAndInvalidate("UninitializedVariableReadNode");

      if (contextLevel > 0) {
        NonLocalVariableReadNode node = NonLocalVariableReadNodeFactory.create(
            contextLevel, variable.getSlot(), getSourceSection());
        return replace(node).executeGeneric(frame);
      } else {
        assert frame.getFrameDescriptor().findFrameSlot(variable.getSlotIdentifier()) == variable.getSlot();
        LocalVariableReadNode node = LocalVariableReadNodeFactory.create(variable, getSourceSection());
        return replace(node).executeGeneric(frame);
      }
    }

    @Override
    public void replaceWithIndependentCopyForInlining(final Inliner inliner) {
      FrameSlot varSlot = inliner.getFrameSlot(this, variable.getSlotIdentifier());
      assert varSlot != null;
      replace(new UninitializedVariableReadNode(this, varSlot));
    }
  }

  public static final class UninitializedSuperReadNode extends ContextualNode {
    private final SSymbol holderClass;
    private final boolean classSide;

    public UninitializedSuperReadNode(final int contextLevel,
        final SSymbol holderClass, final boolean classSide,
        final SourceSection source) {
      super(contextLevel, source);
      this.holderClass = holderClass;
      this.classSide   = classSide;
    }

    private SClass getLexicalSuperClass() {
      SClass clazz = (SClass) Universe.current().getGlobal(holderClass);
      if (classSide) {
        clazz = clazz.getSOMClass();
      }
      return (SClass) clazz.getSuperClass();
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      transferToInterpreterAndInvalidate("UninitializedSuperReadNode");

      if (accessesOuterContext()) {
        NonLocalSuperReadNode node = new NonLocalSuperReadNode(contextLevel,
            getLexicalSuperClass(), getSourceSection());
        return replace(node).
            executeGeneric(frame);
      } else {
        LocalSuperReadNode node = new LocalSuperReadNode(
            getLexicalSuperClass(), getSourceSection());
        return replace(node).
            executeGeneric(frame);
      }
    }
  }

  public static final class UninitializedVariableWriteNode extends UninitializedVariableNode {
    @Child private ExpressionNode exp;

    public UninitializedVariableWriteNode(final Local variable,
        final int contextLevel, final ExpressionNode exp,
        final SourceSection source) {
      super(variable, contextLevel, source);
      this.exp = exp;
    }

    public UninitializedVariableWriteNode(final UninitializedVariableWriteNode node,
        final FrameSlot inlinedVarSlot) {
      this(node.variable.cloneForInlining(inlinedVarSlot),
          node.contextLevel, node.exp, node.getSourceSection());
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      transferToInterpreterAndInvalidate("UninitializedVariableWriteNode");

      if (accessesOuterContext()) {
        NonLocalVariableWriteNode node = NonLocalVariableWriteNodeFactory.create(
            contextLevel, variable.getSlot(), getSourceSection(), exp);
        return replace(node).executeGeneric(frame);
      } else {
        assert frame.getFrameDescriptor().findFrameSlot(variable.getSlotIdentifier()) == variable.getSlot();
        LocalVariableWriteNode node = LocalVariableWriteNodeFactory.create(
            variable, getSourceSection(), exp);
        return replace(node).executeGeneric(frame);
      }
    }

    @Override
    public void replaceWithIndependentCopyForInlining(final Inliner inliner) {
      FrameSlot varSlot = inliner.getFrameSlot(this, variable.getSlotIdentifier());
      assert varSlot != null;
      replace(new UninitializedVariableWriteNode(this, varSlot));
    }
  }
}
