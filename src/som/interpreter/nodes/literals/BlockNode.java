package som.interpreter.nodes.literals;

import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SMethod;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;

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

  public static final class BlockNodeWithContext extends BlockNode {
    private final FrameSlot outerSelfSlot;

    public BlockNodeWithContext(final SMethod blockMethod,
        final Universe universe, final FrameSlot outerSelfSlot) {
      super(blockMethod, universe);
      this.outerSelfSlot = outerSelfSlot;
    }

    @Override
    public SBlock executeSBlock(final VirtualFrame frame) {
      return universe.newBlock(blockMethod, frame.materialize(), outerSelfSlot);
    }
  }
}
