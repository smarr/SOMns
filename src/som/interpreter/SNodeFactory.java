package som.interpreter;

import java.util.List;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.MixinBuilder.MixinDefinitionId;
import som.compiler.MixinDefinition.SlotDefinition;
import som.compiler.Variable.Argument;
import som.compiler.Variable.Local;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.actors.EventualSendNode;
import som.interpreter.nodes.ArgumentReadNode.LocalArgumentReadNode;
import som.interpreter.nodes.ArgumentReadNode.LocalSelfReadNode;
import som.interpreter.nodes.ArgumentReadNode.LocalSuperReadNode;
import som.interpreter.nodes.ArgumentReadNode.NonLocalArgumentReadNode;
import som.interpreter.nodes.ArgumentReadNode.NonLocalSelfReadNode;
import som.interpreter.nodes.ArgumentReadNode.NonLocalSuperReadNode;
import som.interpreter.nodes.ContextualNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.InternalObjectArrayNode;
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
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.interpreter.objectstorage.InitializerFieldWrite;
import som.interpreter.objectstorage.InitializerFieldWriteNodeGen;
import som.vm.NotYetImplementedException;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;


public final class SNodeFactory {

  public static CatchNonLocalReturnNode createCatchNonLocalReturn(
      final ExpressionNode methodBody, final FrameSlot frameOnStackMarker) {
    return new CatchNonLocalReturnNode(methodBody, frameOnStackMarker);
  }

  public static InitializerFieldWrite createFieldWrite(final ExpressionNode self,
      final ExpressionNode exp, final SlotDefinition slot, final SourceSection source) {
    return InitializerFieldWriteNodeGen.create(slot, source, self, exp);
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

  public static ExpressionNode createSelfRead(final int contextLevel,
      final MixinDefinitionId holderMixin, final SourceSection source) {
    if (contextLevel == 0) {
      return new LocalSelfReadNode(holderMixin, source);
    } else {
      return new NonLocalSelfReadNode(holderMixin, contextLevel, source);
    }
  }

  public static ExpressionNode createSuperRead(final int contextLevel,
        final MixinDefinitionId holderMixin, final boolean classSide, final SourceSection source) {
    if (contextLevel == 0) {
      return new LocalSuperReadNode(holderMixin, classSide, source);
    } else {
      return new NonLocalSuperReadNode(contextLevel, holderMixin, classSide, source);
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
    for (ExpressionNode statement : expressions) {
      statement.markAsStatement();
    }

    if (expressions.size() == 0) {
      return new NilLiteralNode(source);
    } else if (expressions.size() == 1) {
      return expressions.get(0);
    }
    return new SequenceNode(expressions.toArray(new ExpressionNode[0]), source);
  }

  public static BlockNode createBlockNode(final SInvokable blockMethod,
      final boolean withContext, final SourceSection source) {
    if (withContext) {
      return new BlockNodeWithContext(blockMethod, source);
    } else {
      return new BlockNode(blockMethod, source);
    }
  }

  public static ExpressionNode createMessageSend(final SSymbol msg,
      final ExpressionNode[] exprs, final boolean eventualSend,
      final SourceSection source, final SourceSection sendOperator) {
    if (eventualSend) {
      return new EventualSendNode(msg, exprs.length,
          new InternalObjectArrayNode(exprs, source), source, sendOperator);
    } else {
      return MessageSendNode.createMessageSend(msg, exprs, source);
    }
  }

  public static AbstractMessageSendNode createMessageSend(final SSymbol msg,
      final List<ExpressionNode> exprs, final SourceSection source) {
    return MessageSendNode.createMessageSend(msg,
        exprs.toArray(new ExpressionNode[0]), source);
  }

  public static ReturnNonLocalNode createNonLocalReturn(final ExpressionNode exp,
      final FrameSlot markerSlot, final int contextLevel,
      final SourceSection source) {
    return new ReturnNonLocalNode(exp, markerSlot, contextLevel, source);
  }

  public static final class NotImplemented extends ExprWithTagsNode {
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
      final MethodScope currentScope, final MixinDefinitionId mixinDefId,
      final SourceSection source) {
    assert mixinDefId != null;
    return new ResolvingImplicitReceiverSend(selector, arguments,
        currentScope, mixinDefId, source);
  }

  public static ExpressionNode createInternalObjectArray(
      final ExpressionNode[] expressions, final SourceSection source) {
    return new InternalObjectArrayNode(expressions, source);
  }
}
