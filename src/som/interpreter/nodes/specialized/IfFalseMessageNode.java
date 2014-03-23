package som.interpreter.nodes.specialized;

import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class IfFalseMessageNode extends AbstractIfMessageNode  {
  public IfFalseMessageNode(final Object rcvr, final Object arg, final Universe universe) { super(rcvr, arg, universe); }
  public IfFalseMessageNode(final IfFalseMessageNode node) { super(node); }

  /**
   * This is the case were we got a block as the argument. Need to actually
   * evaluate it.
   */
  @Specialization(order = 1, guards = "isSameArgument")
  public final Object doIfFalseWithInlining(final VirtualFrame frame,
      final SObject receiver, final SBlock argument) {
    return doIfWithInlining(frame, receiver, argument, universe.falseObject);
  }

  @Specialization(order = 10)
  public final Object doIfFalse(final VirtualFrame frame, final SObject receiver,
      final SBlock argument) {
    return doIf(frame, receiver, argument, universe.falseObject);
  }

  /**
   * The argument in this case is an expression and can be returned directly.
   */
  @Specialization
  public final Object doIfFalse(final VirtualFrame frame,
      final SObject receiver, final Object argument) {
    if (receiver == universe.falseObject) {
      return argument;
    } else {
      return universe.nilObject;
    }
  }
}
