package som.interpreter.nodes.specialized;

import som.vm.constants.Nil;
import som.vmobjects.SBlock;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;


public abstract class IfTrueMessageNode extends AbstractIfMessageNode {
  public IfTrueMessageNode(final Object rcvr, final Object arg, final SourceSection source) { super(rcvr, arg, source); }
  public IfTrueMessageNode(final IfTrueMessageNode node) { super(node); }

  /**
   * This is the case were we got a block as the argument. Need to actually
   * evaluate it.
   */
  @Specialization(guards = "isSameArgument(argument)")
  public final Object doIfTrueWithInlining(final VirtualFrame frame,
      final boolean receiver, final SBlock argument) {
    return doIfWithInlining(frame, receiver, argument, true);
  }

  @Specialization(contains = {"doIfTrueWithInlining"})
  public final Object doIfTrue(final VirtualFrame frame, final boolean receiver,
      final SBlock argument) {
    return doIf(frame, receiver, argument, true);
  }

  /**
   * The argument in this case is an expression and can be returned directly.
   */
  @Specialization
  public final Object doIfTrue(final VirtualFrame frame,
      final boolean receiver, final Object argument) {
    if (condProf.profile(receiver == true)) {
      return argument;
    } else {
      return Nil.nilObject;
    }
  }
}
