package som.interpreter.nodes.literals;

import som.vm.Universe;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;

import com.oracle.truffle.api.frame.VirtualFrame;

public class BlockNode extends LiteralNode {

  protected final SMethod blockMethod;
  protected final Universe universe;

  public BlockNode(final SMethod blockMethod,
      final Universe universe) {
    this.blockMethod = blockMethod;
    this.universe = universe;
  }

  @Override
  public SObject executeGeneric(VirtualFrame frame) {
    return universe.newBlock(blockMethod, frame.materialize(), blockMethod.getNumberOfArguments());
  }

  // TODO: should we do something else for cloneForInlining() in this class?
}
