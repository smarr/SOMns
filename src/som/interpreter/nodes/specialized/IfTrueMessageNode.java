package som.interpreter.nodes.specialized;

import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class IfTrueMessageNode extends AbstractIfMessageNode {
  public IfTrueMessageNode(final Object rcvr, final Object arg, final Universe universe) { super(rcvr, arg, universe); }
  public IfTrueMessageNode(final IfTrueMessageNode node) { super(node); }

  /**
   * This is the case were we got a block as the argument. Need to actually
   * evaluate it.
   */
  @Specialization(order = 1, guards = "isSameArgument")
  public final Object doIfTrueWithInlining(final VirtualFrame frame, final SObject receiver,
      final SBlock argument) {
    return doIfWithInlining(frame, receiver, argument, universe.trueObject);
  }

  @Specialization(order = 10)
  public final Object doIfTrue(final VirtualFrame frame, final SObject receiver,
      final SBlock argument) {
    return doIf(frame, receiver, argument, universe.trueObject);
  }

  /**
   * The argument in this case is an expression and can be returned directly.
   */
  @Specialization(order = 100)
  public final Object doIfTrue(final VirtualFrame frame,
      final SObject receiver, final Object argument) {
    if (receiver == universe.trueObject) {
      return argument;
    } else {
      return universe.nilObject;
    }
  }
}
