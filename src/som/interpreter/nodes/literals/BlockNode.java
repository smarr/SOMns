package som.interpreter.nodes.literals;

import som.interpreter.Arguments;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SMethod;

import com.oracle.truffle.api.frame.VirtualFrame;

public final class BlockNode extends LiteralNode {

  private final SMethod blockMethod;
  private final Universe universe;

  public BlockNode(final SMethod blockMethod,
      final Universe universe) {
    this.blockMethod = blockMethod;
    this.universe = universe;
  }

  @Override
  public SBlock executeSBlock(final VirtualFrame frame) {
    return universe.newBlock(blockMethod, Arguments.get(frame));
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return executeSBlock(frame);
  }
}
