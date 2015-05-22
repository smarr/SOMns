package som.interpreter.nodes.literals;

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
import som.vm.Universe;
import som.vm.constants.Classes;
import som.vm.constants.KernelObj;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable.SMethod;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

public class BlockNode extends LiteralNode {

  protected final SMethod blockMethod;
  protected final SClass  blockClass;

  public BlockNode(final SMethod blockMethod,
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
  public SBlock executeSBlock(final VirtualFrame frame) {
    return new SBlock(KernelObj.kernel, blockMethod, null, blockClass);
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
    SMethod adapted = (SMethod) Universe.newMethod(
        blockMethod.getSignature(),
        AccessModifier.BLOCK_METHOD, Symbols.symbolFor("block method"),
        adaptedForContext, false,
        blockMethod.getEmbeddedBlocks());
    replace(createNode(adapted));
  }

  protected BlockNode createNode(final SMethod adapted) {
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

    public BlockNodeWithContext(final SMethod blockMethod,
        final SourceSection source) {
      super(blockMethod, source);
    }

    public BlockNodeWithContext(final BlockNodeWithContext node) {
      this(node.blockMethod, node.getSourceSection());
    }

    @Override
    public SBlock executeSBlock(final VirtualFrame frame) {
      return new SBlock(KernelObj.kernel, blockMethod, frame.materialize(),
          blockClass);
    }

    @Override
    protected BlockNode createNode(final SMethod adapted) {
      return new BlockNodeWithContext(adapted, getSourceSection());
    }
  }
}
