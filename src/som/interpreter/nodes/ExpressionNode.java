package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

import som.vmobjects.Object;

public abstract class ExpressionNode extends SOMNode {

  public abstract Object executeGeneric(VirtualFrame frame);

}
