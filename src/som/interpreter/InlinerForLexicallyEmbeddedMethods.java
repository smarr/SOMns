package som.interpreter;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.MethodBuilder;
import som.compiler.MethodBuilder.MethodDefinitionError;
import som.compiler.Variable.Local;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.SOMNode;
import som.interpreter.nodes.UninitializedVariableNode.UninitializedVariableReadNode;
import som.interpreter.nodes.UninitializedVariableNode.UninitializedVariableWriteNode;


public class InlinerForLexicallyEmbeddedMethods implements NodeVisitor {

  public static ExpressionNode doInline(
      final ExpressionNode body, final MethodBuilder builder,
      final Local[] blockArguments, final Local[] blockLocals,
      final int blockStartIdx) {
    ExpressionNode inlinedBody = NodeUtil.cloneNode(body);

    return NodeVisitorUtil.applyVisitor(inlinedBody,
        new InlinerForLexicallyEmbeddedMethods(builder, blockArguments,
            blockLocals, blockStartIdx));
  }

  private final MethodBuilder builder;
  private final Local[] blockArguments;
  private final int blockStartIdx;

  public InlinerForLexicallyEmbeddedMethods(final MethodBuilder builder,
      final Local[] blockArguments, final Local[] blockLocals,
      final int blockStartIdx) {
    this.builder = builder;
    this.blockArguments = blockArguments;
    this.blockStartIdx  = blockStartIdx;

    addLocalVarsToBuilder(builder, blockLocals);
  }

  /**
   * Populate the builder with the local variables of the block to be embedded.
   */
  private void addLocalVarsToBuilder(final MethodBuilder builder,
      final Local[] blockLocals) {
    for (Local local : blockLocals) {
      try {
        builder.addLocal(getEmbeddedSlotId(local.getSlotIdentifier()), local.source);
      } catch (MethodDefinitionError e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public boolean visit(final Node node) {
    if (node instanceof SOMNode) {
      ((SOMNode) node).replaceWithLexicallyEmbeddedNode(this);
    }
    return true;
  }

  public UninitializedVariableReadNode getLocalRead(final Object slotIdentifier, final SourceSection source) {
    String inlinedId = getEmbeddedSlotId(slotIdentifier);
    try {
      builder.addLocalIfAbsent(inlinedId, source);
      return (UninitializedVariableReadNode) builder.getReadNode(inlinedId, source);
    } catch (MethodDefinitionError e) {
      throw new RuntimeException(e);
    }
  }

  private String getEmbeddedSlotId(final Object slotIdentifier) {
    String id = (String) slotIdentifier;
    String inlinedId = id + ":" + blockStartIdx;
    return inlinedId;
  }

  public FrameSlot addLocalSlot(final Object orgSlotId,
      final SourceSection source) {
    String id = getEmbeddedSlotId(orgSlotId);
    try {
      return builder.addLocalIfAbsent(id, source).getSlot();
    } catch (MethodDefinitionError e) {
      throw new RuntimeException(e);
    }
  }

  public FrameSlot getLocalSlot(final Object orgSlotId) {
    String id = getEmbeddedSlotId(orgSlotId);
    Local var = builder.getEmbeddedLocal(id);
    return var.getSlot();
  }

  public MethodScope getCurrentMethodScope() {
    return builder.getCurrentMethodScope();
  }

  public UninitializedVariableWriteNode getLocalWrite(final Object slotIdentifier,
      final ExpressionNode valExp,
      final SourceSection source) {
    String inlinedId = getEmbeddedSlotId(slotIdentifier);
    try {
      builder.addLocalIfAbsent(inlinedId, source);
      return (UninitializedVariableWriteNode) builder.getWriteNode(inlinedId,
          valExp, source);
    } catch (MethodDefinitionError e) {
      throw new RuntimeException(e);
    }
  }

  public ExpressionNode getReplacementForLocalArgument(final int argumentIndex,
      final SourceSection source) {
    return blockArguments[argumentIndex - 1].getReadNode(0, source);
  }

  public ExpressionNode getReplacementForNonLocalArgument(final int contextLevel,
      final int argumentIndex, final SourceSection source) {
    assert contextLevel > 0;
    return blockArguments[argumentIndex - 1].getReadNode(contextLevel, source);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + builder.getSignature() + "]";
  }
}
