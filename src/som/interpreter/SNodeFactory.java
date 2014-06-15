package som.interpreter;

import java.util.LinkedHashMap;
import java.util.List;

import som.compiler.Variable;
import som.compiler.Variable.Argument;
import som.compiler.Variable.Local;
import som.interpreter.nodes.ArgumentInitializationNode;
import som.interpreter.nodes.ArgumentReadNode;
import som.interpreter.nodes.ContextualNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.FieldNode.AbstractFieldReadNode;
import som.interpreter.nodes.FieldNode.AbstractFieldWriteNode;
import som.interpreter.nodes.FieldNode.UnenforcedFieldReadNode;
import som.interpreter.nodes.FieldNodeFactory.UnenforcedFieldWriteNodeFactory;
import som.interpreter.nodes.GlobalNode;
import som.interpreter.nodes.GlobalNode.UninitializedGlobalReadNode;
import som.interpreter.nodes.LocalVariableNode.LocalVariableWriteNode;
import som.interpreter.nodes.LocalVariableNodeFactory.LocalVariableWriteNodeFactory;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.ReturnNonLocalNode;
import som.interpreter.nodes.ReturnNonLocalNode.CatchNonLocalReturnNode;
import som.interpreter.nodes.SequenceNode;
import som.interpreter.nodes.UninitializedVariableNode.UninitializedSuperReadNode;
import som.interpreter.nodes.UninitializedVariableNode.UninitializedVariableReadNode;
import som.interpreter.nodes.UninitializedVariableNode.UninitializedVariableWriteNode;
import som.interpreter.nodes.enforced.EnforcedFieldReadNode;
import som.interpreter.nodes.enforced.EnforcedFieldWriteNodeFactory;
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.literals.BlockNode.BlockNodeWithContext;
import som.vm.Universe;
import som.vmobjects.SInvokable.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.FrameSlot;



public final class SNodeFactory {

  public static ArgumentInitializationNode createArgumentInitialization(
      final ExpressionNode methodBody,
      final LinkedHashMap<String, Argument> arguments,
      final boolean executeEnforced) {
    LocalVariableWriteNode[] writes = new LocalVariableWriteNode[arguments.size()];

    for (Argument arg : arguments.values()) {
      writes[arg.index] = LocalVariableWriteNodeFactory.create(
          arg.slot, null, executeEnforced,
          new ArgumentReadNode(arg.index, executeEnforced));
    }
    return new ArgumentInitializationNode(writes, methodBody, executeEnforced);
  }

  public static CatchNonLocalReturnNode createCatchNonLocalReturn(
      final ExpressionNode methodBody, final FrameSlot frameOnStackMarker,
      final SourceSection source, final boolean executeEnforced) {
    return new CatchNonLocalReturnNode(methodBody, frameOnStackMarker,
        source, executeEnforced);
  }

  public static AbstractFieldReadNode createFieldRead(final ExpressionNode self,
      final int fieldIndex, final SourceSection source,
      final boolean executesEnforced) {
    if (executesEnforced) {
      return new EnforcedFieldReadNode(self, fieldIndex, source);
    } else {
      return new UnenforcedFieldReadNode(self, fieldIndex, source);
    }
  }

  public static GlobalNode createGlobalRead(final String name,
      final Universe universe, final SourceSection source,
      final boolean executeEnforced) {
    return createGlobalRead(universe.symbolFor(name), universe, source, executeEnforced);
  }

  public static GlobalNode createGlobalRead(final SSymbol name,
      final Universe universe, final SourceSection source, final boolean executeEnforced) {
    return new UninitializedGlobalReadNode(name, universe, source, executeEnforced);
  }

  public static AbstractFieldWriteNode createFieldWrite(final ExpressionNode self,
      final ExpressionNode exp, final int fieldIndex,
      final SourceSection source, final boolean executeEnforced) {
    if (executeEnforced) {
      return EnforcedFieldWriteNodeFactory.create(fieldIndex, source, self, exp);
    } else {
      return UnenforcedFieldWriteNodeFactory.create(fieldIndex, source, self, exp);
    }
  }

  public static ContextualNode createVariableRead(final Variable variable,
      final int contextLevel, final FrameSlot localSelf,
      final SourceSection source, final boolean executeEnforced) {
    return new UninitializedVariableReadNode(variable, contextLevel, localSelf, source, executeEnforced);
  }

  public static ContextualNode createSuperRead(final Variable variable,
        final int contextLevel, final FrameSlot localSelf,
        final SSymbol holderClass, final boolean classSide,
        final SourceSection source, final boolean executeEnforced) {
    return new UninitializedSuperReadNode(variable, contextLevel, localSelf,
        holderClass, classSide, source, executeEnforced);
  }

  public static ContextualNode createVariableWrite(final Local variable,
        final int contextLevel, final FrameSlot localSelf,
        final ExpressionNode exp, final SourceSection source,
        final boolean executeEnforced) {
    assert exp.nodeExecutesEnforced() == executeEnforced;
    return new UninitializedVariableWriteNode(variable, contextLevel, localSelf,
        exp, source, executeEnforced);
  }

  public static LocalVariableWriteNode createLocalVariableWrite(
      final FrameSlot varSlot, final ExpressionNode exp,
      final SourceSection source, final boolean executeEnforced) {
    return LocalVariableWriteNodeFactory.create(varSlot, source, executeEnforced, exp);
  }

  public static SequenceNode createSequence(final List<ExpressionNode> exps,
      final SourceSection source, final boolean executeEnforced) {
    return new SequenceNode(exps.toArray(new ExpressionNode[0]), source, executeEnforced);
  }

  public static BlockNode createBlockNode(final SMethod blockMethod,
      final boolean withContext, final Universe universe,
      final SourceSection source, final boolean executesEnforced) {
    if (withContext) {
      return new BlockNodeWithContext(blockMethod, universe, source, executesEnforced);
    } else {
      return new BlockNode(blockMethod, universe, source, executesEnforced);
    }
  }

  public static ExpressionNode createMessageSend(final SSymbol msg,
      final ExpressionNode[] exprs, final SourceSection source,
      final boolean executeEnforced) {
    for (ExpressionNode n : exprs) {
      assert n.nodeExecutesEnforced() == executeEnforced;
    }

    return MessageSendNode.create(msg, exprs, source, executeEnforced);
  }

  public static ExpressionNode createMessageSend(final SSymbol msg,
      final List<ExpressionNode> exprs, final SourceSection source,
      final boolean executeEnforced) {
    for (ExpressionNode n : exprs) {
      assert n.nodeExecutesEnforced() == executeEnforced;
    }

    return MessageSendNode.create(msg, exprs.toArray(new ExpressionNode[0]),
        source, executeEnforced);
  }

  public static ReturnNonLocalNode createNonLocalReturn(final ExpressionNode exp,
      final FrameSlot markerSlot, final FrameSlot outerSelf,
      final int contextLevel, final Universe universe,
      final FrameSlot localSelf, final SourceSection source,
      final boolean executeEnforced) {
    return new ReturnNonLocalNode(exp, markerSlot, outerSelf, contextLevel,
        universe, localSelf, source, executeEnforced);
  }
}
