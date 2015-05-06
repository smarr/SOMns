package som.interpreter.nodes.literals;

import som.compiler.MethodGenerationContext;
import som.interpreter.InlinerAdaptToEmbeddedOuterContext;
import som.interpreter.InlinerForLexicallyEmbeddedMethods;
import som.interpreter.Invokable;
import som.interpreter.Method;
import som.interpreter.SplitterForLexicallyEmbeddedCode;
import som.interpreter.nodes.ExpressionNode;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable.SMethod;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

public class BlockNode extends LiteralNode {

  protected final SMethod  blockMethod;

  public BlockNode(final SMethod blockMethod,
      final SourceSection source) {
    super(source);
    this.blockMethod  = blockMethod;
  }

  @Override
  public SBlock executeSBlock(final VirtualFrame frame) {
    return Universe.newBlock(blockMethod, null);
  }

  @Override
  public final Object executeGeneric(final VirtualFrame frame) {
    return executeSBlock(frame);
  }

  @Override
  public void replaceWithIndependentCopyForInlining(final SplitterForLexicallyEmbeddedCode inliner) {
    Invokable clonedInvokable = blockMethod.getInvokable().
        cloneWithNewLexicalContext(inliner.getLexicalContext());
    replaceAdapted(clonedInvokable);
  }

  @Override
  public void replaceWithLexicallyEmbeddedNode(
      final InlinerForLexicallyEmbeddedMethods inliner) {
    Invokable adapted = ((Method) blockMethod.getInvokable()).
        cloneAndAdaptToEmbeddedOuterContext(inliner);
    replaceAdapted(adapted);
  }

  @Override
  public void replaceWithCopyAdaptedToEmbeddedOuterContext(
      final InlinerAdaptToEmbeddedOuterContext inliner) {
    Invokable adapted = ((Method) blockMethod.getInvokable()).
        cloneAndAdaptToSomeOuterContextBeingEmbedded(inliner);
    replaceAdapted(adapted);
  }

  private void replaceAdapted(final Invokable adaptedForContext) {
    SMethod adapted = (SMethod) Universe.newMethod(
        blockMethod.getSignature(), adaptedForContext, false,
        blockMethod.getEmbeddedBlocks());
    replace(createNode(adapted));
  }

  protected BlockNode createNode(final SMethod adapted) {
    return new BlockNode(adapted, getSourceSection());
  }

  @Override
  public ExpressionNode inline(final MethodGenerationContext mgenc) {
    // TODO: currently, we don't support other kinds of blocks
    assert blockMethod.getNumberOfArguments() == 1;
    return blockMethod.getInvokable().inline(mgenc);
  }

  public static final class BlockNodeWithContext extends BlockNode {

    public BlockNodeWithContext(final SMethod blockMethod,
        final SourceSection source) {
      super(blockMethod, source);
    }

    public BlockNodeWithContext(final BlockNodeWithContext node) {
      this(node.blockMethod, node.getSourceSection());
    }

    @Override
    public SBlock executeSBlock(final VirtualFrame frame) {
      return Universe.newBlock(blockMethod, frame.materialize());
    }

    @Override
    protected BlockNode createNode(final SMethod adapted) {
      return new BlockNodeWithContext(adapted, getSourceSection());
    }
  }
}
