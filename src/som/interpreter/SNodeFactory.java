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

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.FrameSlot;



public final class SNodeFactory {

  public static ArgumentInitializationNode createArgumentInitialization(
      final ExpressionNode methodBody, final LinkedHashMap<String, Argument> arguments) {
    LocalVariableWriteNode[] writes = new LocalVariableWriteNode[arguments.size()];

    for (Argument arg : arguments.values()) {
      writes[arg.index] = LocalVariableWriteNodeFactory.create(
          arg.slot, null, new ArgumentReadNode(arg.index));
    }
    return new ArgumentInitializationNode(writes, methodBody);
  }

  public static CatchNonLocalReturnNode createCatchNonLocalReturn(
      final ExpressionNode methodBody, final FrameSlot frameOnStackMarker) {
    return new CatchNonLocalReturnNode(methodBody, frameOnStackMarker);
  }

  public static FieldReadNode createFieldRead(final ExpressionNode self,
      final int fieldIndex, final SourceSection source) {
    return new FieldReadNode(self, fieldIndex, source);
  }

  public static GlobalNode createGlobalRead(final String name,
      final Universe universe, final SourceSection source) {
    return createGlobalRead(universe.symbolFor(name), universe, source);
  }
  public static GlobalNode createGlobalRead(final SSymbol name,
      final Universe universe, final SourceSection source) {
    return new UninitializedGlobalReadNode(name, universe, source);
  }

  public static FieldWriteNode createFieldWrite(final ExpressionNode self,
      final ExpressionNode exp, final int fieldIndex, final SourceSection source) {
    return FieldWriteNodeFactory.create(fieldIndex, source, self, exp);
  }

  public static ContextualNode createVariableRead(final Variable variable,
      final int contextLevel, final FrameSlot localSelf, final SourceSection source) {
    return new UninitializedVariableReadNode(variable, contextLevel, localSelf, source);
  }

  public static ContextualNode createSuperRead(final Variable variable,
        final int contextLevel, final FrameSlot localSelf,
        final SSymbol holderClass, final boolean classSide, final SourceSection source) {
    return new UninitializedSuperReadNode(variable, contextLevel, localSelf,
        holderClass, classSide, source);
  }

  public static ContextualNode createVariableWrite(final Local variable,
        final int contextLevel, final FrameSlot localSelf,
        final ExpressionNode exp, final SourceSection source) {
    return new UninitializedVariableWriteNode(variable, contextLevel, localSelf, exp, source);
  }

  public static LocalVariableWriteNode createLocalVariableWrite(
      final FrameSlot varSlot, final ExpressionNode exp, final SourceSection source) {
    return LocalVariableWriteNodeFactory.create(varSlot, source, exp);
  }
}
