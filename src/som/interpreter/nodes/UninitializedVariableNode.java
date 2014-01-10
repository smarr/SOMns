package som.interpreter.nodes;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.compiler.Variable;
import som.compiler.Variable.Local;
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

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class UninitializedVariableNode extends ContextualNode {
  protected final Variable variable;

  public UninitializedVariableNode(final Variable variable,
      final int contextLevel, final FrameSlot localSelf) {
    super(contextLevel, localSelf);
    this.variable = variable;
  }

  public static final class UninitializedVariableReadNode extends UninitializedVariableNode {
    public UninitializedVariableReadNode(final Variable variable,
        final int contextLevel, final FrameSlot localSelf) {
      super(variable, contextLevel, localSelf);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      transferToInterpreterAndInvalidate("UninitializedVariableReadNode");

      if (contextLevel > 0) {
        NonLocalVariableReadNode node = NonLocalVariableReadNodeFactory.create(contextLevel, variable.slot, localSelf);
        return replace(node).executeGeneric(frame);
      } else {
        LocalVariableReadNode node = LocalVariableReadNodeFactory.create(variable);
        return replace(node).executeGeneric(frame);
      }
    }
  }

  public static class UninitializedSuperReadNode extends UninitializedVariableNode {
    private final SSymbol holderClass;
    private final boolean classSide;

    public UninitializedSuperReadNode(final Variable variable,
        final int contextLevel, final FrameSlot localSelf,
        final SSymbol holderClass, final boolean classSide) {
      super(variable, contextLevel, localSelf);
      this.holderClass = holderClass;
      this.classSide   = classSide;
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

      if (contextLevel > 0) {
        NonLocalSuperReadNode node = NonLocalSuperReadNodeFactory.create(contextLevel,
            variable.slot, localSelf, getLexicalSuperClass());
        return replace(node).executeGeneric(frame);
      } else {
        LocalSuperReadNode node = LocalSuperReadNodeFactory.create(variable,
            getLexicalSuperClass());
        return replace(node).executeGeneric(frame);
      }
    }
  }

  public static class UninitializedVariableWriteNode extends UninitializedVariableNode {
    @Child private ExpressionNode exp;

    public UninitializedVariableWriteNode(final Local variable,
        final int contextLevel, final FrameSlot localSelf, final ExpressionNode exp) {
      super(variable, contextLevel, localSelf);
      this.exp = adoptChild(exp);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      transferToInterpreterAndInvalidate("UninitializedVariableWriteNode");

      if (contextLevel > 0) {
        NonLocalVariableWriteNode node = NonLocalVariableWriteNodeFactory.create(contextLevel, variable.slot, localSelf, exp);
        return replace(node).executeGeneric(frame);
      } else {
        LocalVariableWriteNode node = LocalVariableWriteNodeFactory.create((Local) variable, exp);
        return replace(node).executeGeneric(frame);
      }
    }
  }
}
