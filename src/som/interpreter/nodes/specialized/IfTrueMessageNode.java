package som.interpreter.nodes.specialized;

import som.interpreter.nodes.messages.BinarySendNode;
import som.vmobjects.SBlock;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class IfTrueMessageNode extends BinarySendNode {
  public IfTrueMessageNode(final BinarySendNode node) { super(node); }
  public IfTrueMessageNode(final IfTrueMessageNode node) { super(node); }

  /**
   * This is the case were we got a block as the argument. Need to actually
   * evaluate it.
   */
  @Specialization
  public Object doIfTrue(final VirtualFrame frame,
      final SObject receiver, final SBlock argument) {
    if (receiver == universe.trueObject) {
      SMethod blockMethod = argument.getMethod();
      SBlock b = universe.newBlock(blockMethod, frame.materialize(), 1);
      return blockMethod.invoke(frame.pack(), b);
    } else {
      return universe.nilObject;
    }
  }

  /**
   * The argument in this case is an expression and can be returned directly.
   */
  @Specialization
  public Object doIfTrue(final VirtualFrame frame,
      final SObject receiver, final Object argument) {
    if (receiver == universe.trueObject) {
      return argument;
    } else {
      return universe.nilObject;
    }
  }

}
