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

  public BlockNode(final SMethod blockMethod, final SourceSection source,
      final boolean executesEnforced) {
    super(source, executesEnforced);
    this.blockMethod = blockMethod;
  }

  @Override
  public SBlock executeSBlock(final VirtualFrame frame) {
    return Universe.newBlock(blockMethod, null, executesEnforced);
  }

  @Override
  public final Object executeGeneric(final VirtualFrame frame) {
    return executeSBlock(frame);
  }

  @Override
  public void replaceWithIndependentCopyForInlining(final Inliner inliner) {
    SMethod forInlining = (SMethod) cloneMethod(inliner);
    replace(new BlockNode(forInlining, getSourceSection(), executesEnforced));
  }

  protected SInvokable cloneMethod(final Inliner inliner) {
    AbstractInvokable clonedEnforcedInvokable;
    AbstractInvokable clonedUnenforcedInvokable;

    if (blockMethod.isUnenforced()) {
      clonedEnforcedInvokable = null;
    } else {
      clonedEnforcedInvokable = blockMethod.getEnforcedInvokable().
        cloneWithNewLexicalContext(inliner.getLexicalContext());
    }
    clonedUnenforcedInvokable = blockMethod.getUnenforcedInvokable().
        cloneWithNewLexicalContext(inliner.getLexicalContext());

    SInvokable forInlining = Universe.newMethod(blockMethod.getSignature(),
        clonedEnforcedInvokable, clonedUnenforcedInvokable, false,
        new SMethod[0], blockMethod.isUnenforced());
    return forInlining;
  }

  public static final class BlockNodeWithContext extends BlockNode {

    public BlockNodeWithContext(final SMethod blockMethod,
        final SourceSection source, final boolean executesEnforced) {
      super(blockMethod, source, executesEnforced);
    }

    public BlockNodeWithContext(final BlockNodeWithContext node) {
      this(node.blockMethod, node.getSourceSection(),
          node.executesEnforced);
    }

    @Override
    public SBlock executeSBlock(final VirtualFrame frame) {
      return Universe.newBlock(blockMethod, frame.materialize(), executesEnforced);
    }

    @Override
    public void replaceWithIndependentCopyForInlining(final Inliner inliner) {
      SMethod forInlining = (SMethod) cloneMethod(inliner);
      replace(new BlockNodeWithContext(forInlining, getSourceSection(),
          executesEnforced));
    }
  }
}
