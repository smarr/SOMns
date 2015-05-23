package som.interpreter;

import java.util.List;

import som.compiler.ClassBuilder.ClassDefinitionId;
import som.compiler.Variable.Argument;
import som.compiler.Variable.Local;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.nodes.ArgumentReadNode.LocalArgumentReadNode;
import som.interpreter.nodes.ArgumentReadNode.LocalSuperReadNode;
import som.interpreter.nodes.ArgumentReadNode.NonLocalArgumentReadNode;
import som.interpreter.nodes.ArgumentReadNode.NonLocalSuperReadNode;
import som.interpreter.nodes.ContextualNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.FieldNode.FieldReadNode;
import som.interpreter.nodes.FieldNode.FieldWriteNode;
import som.interpreter.nodes.FieldNodeFactory.FieldWriteNodeGen;
import som.interpreter.nodes.LocalVariableNode.LocalVariableWriteNode;
import som.interpreter.nodes.LocalVariableNodeFactory.LocalVariableWriteNodeGen;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.interpreter.nodes.ResolvingImplicitReceiverSend;
import som.interpreter.nodes.ReturnNonLocalNode;
import som.interpreter.nodes.ReturnNonLocalNode.CatchNonLocalReturnNode;
import som.interpreter.nodes.SequenceNode;
import som.interpreter.nodes.UninitializedVariableNode.UninitializedVariableReadNode;
import som.interpreter.nodes.UninitializedVariableNode.UninitializedVariableWriteNode;
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.literals.BlockNode.BlockNodeWithContext;
import som.interpreter.nodes.literals.NilLiteralNode;
import som.vm.NotYetImplementedException;
import som.vmobjects.SInvokable.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;



public final class SNodeFactory {

  public static CatchNonLocalReturnNode createCatchNonLocalReturn(
      final ExpressionNode methodBody, final FrameSlot frameOnStackMarker) {
    return new CatchNonLocalReturnNode(methodBody, frameOnStackMarker);
  }

  public static FieldReadNode createFieldRead(final ExpressionNode self,
      final int fieldIndex, final SourceSection source) {
    return new FieldReadNode(self, fieldIndex, source);
  }

  public static FieldWriteNode createFieldWrite(final ExpressionNode self,
      final ExpressionNode exp, final int fieldIndex, final SourceSection source) {
    return FieldWriteNodeGen.create(fieldIndex, source, self, exp);
  }

  public static ContextualNode createLocalVarRead(final Local variable,
      final int contextLevel, final SourceSection source) {
    return new UninitializedVariableReadNode(variable, contextLevel, source);
  }

  public static ExpressionNode createArgumentRead(final Argument variable,
      final int contextLevel, final SourceSection source) {
    if (contextLevel == 0) {
      return new LocalArgumentReadNode(variable.index, source);
    } else {
      return new NonLocalArgumentReadNode(variable.index, contextLevel, source);
    }
  }

  public static ExpressionNode createSuperRead(final int contextLevel,
        final ClassDefinitionId holderClass, final boolean classSide, final SourceSection source) {
    if (contextLevel == 0) {
      return new LocalSuperReadNode(holderClass, classSide, source);
    } else {
      return new NonLocalSuperReadNode(contextLevel, holderClass, classSide, source);
    }
  }

  public static ContextualNode createVariableWrite(final Local variable,
        final int contextLevel,
        final ExpressionNode exp, final SourceSection source) {
    return new UninitializedVariableWriteNode(variable, contextLevel, exp, source);
  }

  public static LocalVariableWriteNode createLocalVariableWrite(
      final FrameSlot varSlot, final ExpressionNode exp, final SourceSection source) {
    return LocalVariableWriteNodeGen.create(varSlot, source, exp);
  }

  public static ExpressionNode createSequence(
      final List<ExpressionNode> expressions, final SourceSection source) {
    if (expressions.size() == 0) {
      return new NilLiteralNode(source);
    } else if (expressions.size() == 1) {
      return expressions.get(0);
    }
    return new SequenceNode(expressions.toArray(new ExpressionNode[0]), source);
  }

  public static BlockNode createBlockNode(final SMethod blockMethod,
      final boolean withContext, final SourceSection source) {
    if (withContext) {
      return new BlockNodeWithContext(blockMethod, source);
    } else {
      return new BlockNode(blockMethod, source);
    }
  }

  public static AbstractMessageSendNode createMessageSend(final SSymbol msg,
      final ExpressionNode[] exprs, final SourceSection source) {
    return MessageSendNode.createSelfSend(msg, exprs, source);
  }

  public static AbstractMessageSendNode createMessageSend(final SSymbol msg,
      final List<ExpressionNode> exprs, final SourceSection source) {
    return MessageSendNode.createSelfSend(msg, exprs.toArray(new ExpressionNode[0]), source);
  }

  public static ReturnNonLocalNode createNonLocalReturn(final ExpressionNode exp,
      final FrameSlot markerSlot, final int contextLevel,
      final SourceSection source) {
    return new ReturnNonLocalNode(exp, markerSlot, contextLevel, source);
  }

  public static final class NotImplemented extends ExpressionNode {
    private final String msg;

    public NotImplemented(final String msg, final SourceSection source) {
      super(source);
      this.msg = msg;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      throw new NotYetImplementedException();
    }

    @Override
    public String toString() {
      return "Not Implemented: " + msg;
    }
  }

  public static ExpressionNode createImplicitReceiverSend(
      final SSymbol selector, final ExpressionNode[] arguments,
      final MethodScope currentScope, final ClassDefinitionId classDefId,
      final SourceSection source) {
    assert classDefId != null;
    return new ResolvingImplicitReceiverSend(selector, arguments,
        currentScope, classDefId, source);
  }

  public static ExpressionNode createImplicitReceiverSetterSend(
      final SSymbol identifier, final ExpressionNode exp,
      final SourceSection source) {
    return new NotImplemented("implicit receiver setter send", source);
  }
}
