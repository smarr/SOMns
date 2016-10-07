package som.interpreter.nodes.literals;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.AccessModifier;
import som.compiler.MethodBuilder;
import som.compiler.Variable.Local;
import som.interpreter.InlinerAdaptToEmbeddedOuterContext;
import som.interpreter.InlinerForLexicallyEmbeddedMethods;
import som.interpreter.Invokable;
import som.interpreter.Method;
import som.interpreter.SplitterForLexicallyEmbeddedCode;
import som.interpreter.nodes.ExpressionNode;
import som.vm.Symbols;
import som.vm.constants.Classes;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import tools.debugger.Tags.LiteralTag;

public class BlockNode extends LiteralNode {

  protected final SInvokable blockMethod;
  protected final SClass  blockClass;

  public BlockNode(final SInvokable blockMethod,
      final SourceSection source) {
    super(source);
    this.blockMethod  = blockMethod;
    switch (blockMethod.getNumberOfArguments()) {
      case 1: { this.blockClass = Classes.blockClass1; break; }
      case 2: { this.blockClass = Classes.blockClass2; break; }
      case 3: { this.blockClass = Classes.blockClass3; break; }

      // we don't support more than 3 arguments
      default : this.blockClass = Classes.blockClass;
    }
  }

  @Override
  protected boolean isTaggedWith(final Class<?> tag) {
    if (LiteralTag.class == tag) {
      return false; // Blocks should not be indicated as literals, looks strange.
    } else {
      return super.isTaggedWith(tag);
    }
  }

  public SInvokable getBlockMethod() {
    return blockMethod;
  }

  @Override
  public SBlock executeSBlock(final VirtualFrame frame) {
    return new SBlock(blockMethod, null, blockClass);
  }

  @Override
  public final Object executeGeneric(final VirtualFrame frame) {
    return executeSBlock(frame);
  }

  @Override
  public void replaceWithIndependentCopyForInlining(final SplitterForLexicallyEmbeddedCode inliner) {
    Invokable clonedInvokable = blockMethod.getInvokable().
        cloneWithNewLexicalContext(inliner.getCurrentScope());
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
    SInvokable adapted = new SInvokable(blockMethod.getSignature(),
        AccessModifier.BLOCK_METHOD, Symbols.BLOCK_METHOD,
        adaptedForContext, blockMethod.getEmbeddedBlocks());
    replace(createNode(adapted));
  }

  protected BlockNode createNode(final SInvokable adapted) {
    return new BlockNode(adapted, getSourceSection());
  }

  @Override
  public ExpressionNode inline(final MethodBuilder builder,
      final Local... blockArguments) {
    // self doesn't need to be passed
    assert blockMethod.getNumberOfArguments() - 1 == blockArguments.length;
    return blockMethod.getInvokable().inline(builder, blockArguments);
  }

  public static final class BlockNodeWithContext extends BlockNode {

    public BlockNodeWithContext(final SInvokable blockMethod,
        final SourceSection source) {
      super(blockMethod, source);
    }

    public BlockNodeWithContext(final BlockNodeWithContext node) {
      this(node.blockMethod, node.getSourceSection());
    }

    @Override
    public SBlock executeSBlock(final VirtualFrame frame) {
      return new SBlock(blockMethod, frame.materialize(), blockClass);
    }

    @Override
    protected BlockNode createNode(final SInvokable adapted) {
      return new BlockNodeWithContext(adapted, getSourceSection());
    }
  }
}
