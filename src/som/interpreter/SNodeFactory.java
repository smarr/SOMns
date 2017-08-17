package som.interpreter;

import java.util.List;

import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.compiler.MixinDefinition.SlotDefinition;
import som.compiler.Variable.Internal;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.actors.EventualSendNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.InternalObjectArrayNode;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.ResolvingImplicitReceiverSend;
import som.interpreter.nodes.ReturnNonLocalNode.CatchNonLocalReturnNode;
import som.interpreter.nodes.SequenceNode;
import som.interpreter.nodes.literals.NilLiteralNode;
import som.interpreter.objectstorage.InitializerFieldWrite;
import som.interpreter.objectstorage.InitializerFieldWriteNodeGen;
import som.vmobjects.SSymbol;


public final class SNodeFactory {

  public static CatchNonLocalReturnNode createCatchNonLocalReturn(
      final ExpressionNode methodBody, final Internal frameOnStackMarker) {
    return new CatchNonLocalReturnNode(
        methodBody, frameOnStackMarker).initialize(methodBody.getSourceSection());
  }

  public static InitializerFieldWrite createFieldWrite(final ExpressionNode self,
      final ExpressionNode exp, final SlotDefinition slot, final SourceSection source) {
    return InitializerFieldWriteNodeGen.create(slot, self, exp).initialize(source);
  }

  public static ExpressionNode createSequence(
      final List<ExpressionNode> expressions, final SourceSection source) {
    for (ExpressionNode statement : expressions) {
      statement.markAsStatement();
    }

    if (expressions.size() == 0) {
      return new NilLiteralNode().initialize(source);
    } else if (expressions.size() == 1) {
      return expressions.get(0);
    }

    SequenceNode s = new SequenceNode(expressions.toArray(new ExpressionNode[0]));
    return s.initialize(source);
  }

  public static ExpressionNode createMessageSend(final SSymbol msg,
      final ExpressionNode[] exprs, final boolean eventualSend,
      final SourceSection source, final SourceSection sendOperator,
      final SomLanguage lang) {
    if (eventualSend) {
      return new EventualSendNode(msg, exprs.length,
          new InternalObjectArrayNode(exprs).initialize(source), source, sendOperator, lang);
    } else {
      return MessageSendNode.createMessageSend(msg, exprs, source, lang.getVM());
    }
  }

  public static ExpressionNode createMessageSend(final SSymbol msg,
      final List<ExpressionNode> exprs, final SourceSection source, final VM vm) {
    return MessageSendNode.createMessageSend(msg,
        exprs.toArray(new ExpressionNode[0]), source, vm);
  }

  public static ExpressionNode createImplicitReceiverSend(
      final SSymbol selector, final ExpressionNode[] arguments,
      final MethodScope currentScope, final MixinDefinitionId mixinDefId,
      final SourceSection source, final VM vm) {
    assert mixinDefId != null;
    return new ResolvingImplicitReceiverSend(selector, arguments,
        currentScope, mixinDefId, vm).initialize(source);
  }

  public static ExpressionNode createInternalObjectArray(
      final ExpressionNode[] expressions, final SourceSection source) {
    return new InternalObjectArrayNode(expressions).initialize(source);
  }
}
