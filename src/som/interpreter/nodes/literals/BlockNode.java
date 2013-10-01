package som.interpreter.nodes.literals;

import som.vm.Universe;
import som.vmobjects.Method;
import som.vmobjects.Object;

import com.oracle.truffle.api.frame.VirtualFrame;

public class BlockNode extends LiteralNode {

  protected final Method blockMethod;
  protected final Universe universe;

  public BlockNode(final Method blockMethod,
      final Universe universe) {
    this.blockMethod = blockMethod;
    this.universe = universe;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return universe.newBlock(blockMethod, frame.materialize(), blockMethod.getNumberOfArguments());
  }

  // TODO: should we do something else for cloneForInlining() in this class?
}
