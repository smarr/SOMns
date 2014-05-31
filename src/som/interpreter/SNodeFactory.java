package som.interpreter;

import java.util.LinkedHashMap;

import som.compiler.Variable;
import som.compiler.Variable.Argument;
import som.compiler.Variable.Local;
import som.interpreter.nodes.ArgumentInitializationNode;
import som.interpreter.nodes.ArgumentReadNode;
import som.interpreter.nodes.ContextualNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.FieldNode.FieldReadNode;
import som.interpreter.nodes.FieldNode.FieldWriteNode;
import som.interpreter.nodes.FieldNodeFactory.FieldWriteNodeFactory;
import som.interpreter.nodes.GlobalNode;
import som.interpreter.nodes.GlobalNode.UninitializedGlobalReadNode;
import som.interpreter.nodes.LocalVariableNode.LocalVariableWriteNode;
import som.interpreter.nodes.LocalVariableNodeFactory.LocalVariableWriteNodeFactory;
import som.interpreter.nodes.ReturnNonLocalNode.CatchNonLocalReturnNode;
import som.interpreter.nodes.UninitializedVariableNode.UninitializedSuperReadNode;
import som.interpreter.nodes.UninitializedVariableNode.UninitializedVariableReadNode;
import som.interpreter.nodes.UninitializedVariableNode.UninitializedVariableWriteNode;
import som.vm.Universe;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.FrameSlot;



public final class SNodeFactory {

  public static ArgumentInitializationNode createArgumentInitialization(
      final ExpressionNode methodBody, final LinkedHashMap<String, Argument> arguments) {
    LocalVariableWriteNode[] writes = new LocalVariableWriteNode[arguments.size()];

    for (Argument arg : arguments.values()) {
      writes[arg.index] = LocalVariableWriteNodeFactory.create(
          arg.slot, new ArgumentReadNode(arg.index));
    }
    return new ArgumentInitializationNode(writes, methodBody);
  }

  public static CatchNonLocalReturnNode createCatchNonLocalReturn(
      final ExpressionNode methodBody, final FrameSlot frameOnStackMarker) {
    return new CatchNonLocalReturnNode(methodBody, frameOnStackMarker);
  }

  public static FieldReadNode createFieldRead(final ExpressionNode self,
      final int fieldIndex) {
    return new FieldReadNode(self, fieldIndex);
  }

  public static GlobalNode createGlobalRead(final SSymbol name,
      final Universe universe) {
    return new UninitializedGlobalReadNode(name, universe);
  }

  public static FieldWriteNode createFieldWrite(final ExpressionNode self,
      final ExpressionNode exp, final int fieldIndex) {
    return FieldWriteNodeFactory.create(fieldIndex, self, exp);
  }

  public static ContextualNode createVariableRead(final Variable variable,
      final int contextLevel, final FrameSlot localSelf) {
    return new UninitializedVariableReadNode(variable, contextLevel, localSelf);
  }

  public static ContextualNode createSuperRead(final Variable variable,
        final int contextLevel, final FrameSlot localSelf,
        final SSymbol holderClass, final boolean classSide) {
    return new UninitializedSuperReadNode(variable, contextLevel, localSelf,
        holderClass, classSide);
  }

  public static ContextualNode createVariableWrite(final Local variable,
        final int contextLevel, final FrameSlot localSelf,
        final ExpressionNode exp) {
    return new UninitializedVariableWriteNode(variable, contextLevel, localSelf, exp);
  }
}
