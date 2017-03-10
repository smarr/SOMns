package som.interpreter;

import java.util.List;

import com.oracle.truffle.api.source.SourceSection;

import som.compiler.MixinBuilder.MixinDefinitionId;
import som.compiler.MixinDefinition.SlotDefinition;
import som.compiler.Variable.Internal;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.actors.EventualSendNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.InternalObjectArrayNode;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.ResolvingImplicitReceiverSend;
import som.interpreter.nodes.ReturnNonLocalNode;
import som.interpreter.nodes.ReturnNonLocalNode.CatchNonLocalReturnNode;
import som.interpreter.nodes.SequenceNode;
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.literals.BlockNode.BlockNodeWithContext;
import som.interpreter.nodes.literals.NilLiteralNode;
import som.interpreter.objectstorage.InitializerFieldWrite;
import som.interpreter.objectstorage.InitializerFieldWriteNodeGen;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;


public final class SNodeFactory {

  public static CatchNonLocalReturnNode createCatchNonLocalReturn(
      final ExpressionNode methodBody, final Internal frameOnStackMarker) {
    return new CatchNonLocalReturnNode(methodBody, frameOnStackMarker);
  }

  public static InitializerFieldWrite createFieldWrite(final ExpressionNode self,
      final ExpressionNode exp, final SlotDefinition slot, final SourceSection source) {
    return InitializerFieldWriteNodeGen.create(slot, source, self, exp);
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

  public static ExpressionNode createMessageSend(final SSymbol msg,
      final List<ExpressionNode> exprs, final SourceSection source) {
    return MessageSendNode.createMessageSend(msg,
        exprs.toArray(new ExpressionNode[0]), source);
  }

  public static ReturnNonLocalNode createNonLocalReturn(final ExpressionNode exp,
      final Internal marker, final int contextLevel,
      final SourceSection source) {
    return new ReturnNonLocalNode(exp, marker, contextLevel, source);
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
