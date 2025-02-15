package somns.interpreter;

import java.util.List;

import com.oracle.truffle.api.source.SourceSection;

import somns.interpreter.nodes.OuterObjectReadNodeGen;
import somns.interpreter.objectstorage.InitializerFieldWriteNodeGen;
import somns.VM;
import somns.compiler.MixinBuilder;
import somns.compiler.MixinBuilder.MixinDefinitionId;
import somns.compiler.MixinDefinition.SlotDefinition;
import somns.compiler.Variable.Internal;
import somns.interpreter.LexicalScope.MethodScope;
import somns.interpreter.actors.EventualSendNode;
import somns.interpreter.nodes.ExpressionNode;
import somns.interpreter.nodes.InternalObjectArrayNode;
import somns.interpreter.nodes.MessageSendNode;
import somns.interpreter.nodes.ResolvingImplicitReceiverSend;
import somns.interpreter.nodes.SequenceNode;
import somns.interpreter.nodes.ReturnNonLocalNode.CatchNonLocalReturnNode;
import somns.interpreter.nodes.literals.NilLiteralNode;
import somns.interpreter.objectstorage.InitializerFieldWrite;
import somns.vmobjects.SSymbol;


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

  public static ExpressionNode createOuterLookupChain(final List<MixinDefinitionId> outerIds,
      final MixinBuilder enclosing, final ExpressionNode receiver,
      final SourceSection source) {
    MixinDefinitionId currentMixin = enclosing.getMixinId();
    ExpressionNode currentReceiver = receiver;

    for (MixinDefinitionId enclosingMixin : outerIds) {
      currentReceiver =
          OuterObjectReadNodeGen.create(currentMixin, enclosingMixin, currentReceiver)
                                .initialize(source);
      currentMixin = enclosingMixin;
    }

    return currentReceiver;
  }
}
