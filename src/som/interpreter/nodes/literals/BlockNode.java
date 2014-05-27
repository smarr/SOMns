package som.interpreter.nodes.literals;

import som.interpreter.Inliner;
import som.interpreter.Invokable;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SInvokable.SMethod;

import com.oracle.truffle.api.frame.VirtualFrame;

public class BlockNode extends LiteralNode {

  protected final SMethod  blockMethod;
  protected final Universe universe;

  public BlockNode(final SMethod blockMethod, final Universe universe) {
    this.blockMethod  = blockMethod;
    this.universe     = universe;
  }

  @Override
  public SBlock executeSBlock(final VirtualFrame frame) {
    return universe.newBlock(blockMethod, null);
  }

  @Override
  public final Object executeGeneric(final VirtualFrame frame) {
    return executeSBlock(frame);
  }

  @Override
  public void replaceWithIndependentCopyForInlining(final Inliner inliner) {
    SMethod forInlining = (SMethod) cloneMethod(inliner);
    replace(new BlockNode(forInlining, universe));
  }

  protected SInvokable cloneMethod(final Inliner inliner) {
    Invokable clonedInvokable = blockMethod.getInvokable().
        cloneWithNewLexicalContext(inliner.getLexicalContext());
    SInvokable forInlining = universe.newMethod(blockMethod.getSignature(),
        clonedInvokable, false);
    return forInlining;
  }

  public static final class BlockNodeWithContext extends BlockNode {

    public BlockNodeWithContext(final SMethod blockMethod,
        final Universe universe) {
      super(blockMethod, universe);
    }

    public BlockNodeWithContext(final BlockNodeWithContext node) {
      this(node.blockMethod, node.universe);
    }

    @Override
    public SBlock executeSBlock(final VirtualFrame frame) {
      return universe.newBlock(blockMethod, frame.materialize());
    }
  }
}
