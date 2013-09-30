package som.interpreter.nodes.literals;

import som.vm.Universe;
import som.vmobjects.Method;
import som.vmobjects.Object;

import com.oracle.truffle.api.frame.VirtualFrame;

public class BlockNode extends LiteralNode {

  protected final Universe universe;

  public BlockNode(final Method blockMethod,
      final Universe universe) {
    super(blockMethod);
    this.universe = universe;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    Method method = (Method) value;
    return universe.newBlock(method, frame.materialize(), method.getNumberOfArguments());
  }

  // TODO: should we do something else for cloneForInlining() in this class?
}
