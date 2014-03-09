package som.interpreter.nodes.literals;

import static com.oracle.truffle.api.nodes.NodeInfo.Kind.SPECIALIZED;
import som.interpreter.Inliner;
import som.interpreter.Invokable;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SMethod;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo.Kind;

public class BlockNode extends LiteralNode {

  protected final SMethod blockMethod;
  protected final Universe universe;

  public BlockNode(final SMethod blockMethod, final Universe universe) {
    this.blockMethod  = blockMethod;
    this.universe     = universe;
  }

  @Override
  public SBlock executeSBlock(final VirtualFrame frame) {
    return universe.newBlock(blockMethod, null, null);
  }

  @Override
  public final Object executeGeneric(final VirtualFrame frame) {
    return executeSBlock(frame);
  }

  @Override
  public void replaceWithIndependentCopyForInlining(final Inliner inliner) {
    SMethod forInlining = cloneMethod(inliner);
    replace(new BlockNode(forInlining, universe));
  }

  protected SMethod cloneMethod(final Inliner inliner) {
    Invokable clonedInvokable = blockMethod.getInvokable().cloneWithNewLexicalContext(inliner.getLexicalContext());
    SMethod forInlining = universe.newMethod(blockMethod.getSignature(), clonedInvokable, blockMethod.isPrimitive());
    return forInlining;
  }

  @Override
  public Kind getKind() {
      return SPECIALIZED;
  }

  public static final class BlockNodeWithContext extends BlockNode {
    private final FrameSlot outerSelfSlot;
    private final int       contextLevel;

    public BlockNodeWithContext(final SMethod blockMethod,
        final Universe universe, final FrameSlot outerSelfSlot,
        final int contextLevel) {
      super(blockMethod, universe);
      this.outerSelfSlot = outerSelfSlot;
      this.contextLevel  = contextLevel;
    }

    public BlockNodeWithContext(final BlockNodeWithContext node,
        final FrameSlot inlinedOuterSelfSlot) {
      this(node.blockMethod, node.universe, inlinedOuterSelfSlot, node.contextLevel);
    }

    @Override
    public SBlock executeSBlock(final VirtualFrame frame) {
      return universe.newBlock(blockMethod, frame.materialize(), outerSelfSlot);
    }

    @Override
    public void replaceWithIndependentCopyForInlining(final Inliner inliner) {
      FrameSlot inlinedOuterSelfSlot = inliner.getFrameSlot(outerSelfSlot.getIdentifier(), contextLevel);
      assert    inlinedOuterSelfSlot != null;
      replace(new BlockNodeWithContext(cloneMethod(inliner), universe, inlinedOuterSelfSlot, contextLevel));
    }
  }
}
