package som.interpreter;

import som.compiler.MethodBuilder;
import som.compiler.Variable.Local;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.SOMNode;
import som.interpreter.nodes.UninitializedVariableNode.UninitializedVariableReadNode;
import som.interpreter.nodes.UninitializedVariableNode.UninitializedVariableWriteNode;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.source.SourceSection;


public class InlinerForLexicallyEmbeddedMethods implements NodeVisitor {

  public static ExpressionNode doInline(
      final ExpressionNode body, final MethodBuilder builder,
      final Local[] blockArguments,
      final int blockStartIdx) {
    ExpressionNode inlinedBody = NodeUtil.cloneNode(body);

    return NodeVisitorUtil.applyVisitor(inlinedBody,
        new InlinerForLexicallyEmbeddedMethods(builder, blockArguments, blockStartIdx));
  }

  private final MethodBuilder builder;
  private final Local[] blockArguments;
  private final int blockStartIdx;

  public InlinerForLexicallyEmbeddedMethods(final MethodBuilder builder,
      final Local[] blockArguments, final int blockStartIdx) {
    this.builder = builder;
    this.blockArguments = blockArguments;
    this.blockStartIdx  = blockStartIdx;
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
    builder.addLocalIfAbsent(inlinedId);
    return (UninitializedVariableReadNode) builder.getReadNode(inlinedId, source);
  }

  private String getEmbeddedSlotId(final Object slotIdentifier) {
    String id = (String) slotIdentifier;
    String inlinedId = id + ":" + blockStartIdx;
    return inlinedId;
  }

  public FrameSlot addLocalSlot(final Object orgSlotId) {
    String id = getEmbeddedSlotId(orgSlotId);
    assert builder.getEmbeddedLocal(id) == null;
    return builder.addLocal(id).getSlot();
  }

  public FrameSlot getLocalSlot(final Object orgSlotId) {
    String id = getEmbeddedSlotId(orgSlotId);
    Local var = builder.getEmbeddedLocal(id);
    return var.getSlot();
  }

  public LexicalScope getCurrentLexicalScope() {
    return builder.getCurrentLexicalScope();
  }

  public UninitializedVariableWriteNode getLocalWrite(final Object slotIdentifier,
      final ExpressionNode valExp,
      final SourceSection source) {
    String inlinedId = getEmbeddedSlotId(slotIdentifier);
    builder.addLocalIfAbsent(inlinedId);
    return (UninitializedVariableWriteNode) builder.getWriteNode(inlinedId,
        valExp, source);
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
}
