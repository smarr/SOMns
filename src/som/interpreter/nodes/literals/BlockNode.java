package som.interpreter.nodes.literals;

import som.interpreter.AbstractInvokable;
import som.interpreter.Inliner;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SInvokable.SMethod;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

public class BlockNode extends LiteralNode {

  protected final SMethod  blockMethod;
  protected final Universe universe;

  public BlockNode(final SMethod blockMethod, final Universe universe,
      final SourceSection source, final boolean executesEnforced) {
    super(source, executesEnforced);
    this.blockMethod  = blockMethod;
    this.universe     = universe;
  }

  @Override
  public SBlock executeSBlock(final VirtualFrame frame) {
    return universe.newBlock(blockMethod, null, executesEnforced);
  }

  @Override
  public final Object executeGeneric(final VirtualFrame frame) {
    return executeSBlock(frame);
  }

  @Override
  public void replaceWithIndependentCopyForInlining(final Inliner inliner) {
    SMethod forInlining = (SMethod) cloneMethod(inliner);
    replace(new BlockNode(forInlining, universe, getSourceSection(), executesEnforced));
  }

  protected SInvokable cloneMethod(final Inliner inliner) {
    AbstractInvokable clonedInvokable = blockMethod.getInvokable().
        cloneWithNewLexicalContext(inliner.getLexicalContext());
    SInvokable forInlining = universe.newMethod(blockMethod.getSignature(),
        clonedInvokable, false, new SMethod[0], blockMethod.isUnenforced());
    return forInlining;
  }

  public static final class BlockNodeWithContext extends BlockNode {

    public BlockNodeWithContext(final SMethod blockMethod,
        final Universe universe, final SourceSection source, final boolean executesEnforced) {
      super(blockMethod, universe, source, executesEnforced);
    }

    public BlockNodeWithContext(final BlockNodeWithContext node) {
      this(node.blockMethod, node.universe, node.getSourceSection(),
          node.executesEnforced);
    }

    @Override
    public SBlock executeSBlock(final VirtualFrame frame) {
      return universe.newBlock(blockMethod, frame.materialize(), executesEnforced);
    }

    @Override
    public void replaceWithIndependentCopyForInlining(final Inliner inliner) {
      SMethod forInlining = (SMethod) cloneMethod(inliner);
      replace(new BlockNodeWithContext(forInlining, universe,
          getSourceSection(), executesEnforced));
    }
  }
}
