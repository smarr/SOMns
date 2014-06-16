package som.interpreter.nodes.specialized;

import som.vm.NotYetImplementedException;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class WhileWithDynamicBlocksNode extends AbstractWhileNode {
  private final SInvokable conditionMethod;
  private final SInvokable bodyMethod;

  private WhileWithDynamicBlocksNode(final SBlock rcvr, final SBlock arg,
      final boolean predicateBool, final Universe universe,
      final SourceSection source, final boolean executesEnforced) {
    super(rcvr, arg, predicateBool, universe, source, executesEnforced);
    conditionMethod = rcvr.getMethod();
    bodyMethod = arg.getMethod();
  }

  @Override
  public final Object executeGeneric(final VirtualFrame frame) {
    throw new NotYetImplementedException();
  }

  @Override
  public final void executeVoid(final VirtualFrame frame) {
    throw new NotYetImplementedException();
  }

  @Override
  protected final SObject doWhileConditionally(final VirtualFrame frame,
      final SBlock loopCondition,
      final SBlock loopBody) {
    assert loopCondition.getMethod() == conditionMethod;
    assert loopBody.getMethod()      == bodyMethod;
    return doWhileUnconditionally(frame, loopCondition, loopBody);
  }

  public static final class WhileTrueDynamicBlocksNode extends WhileWithDynamicBlocksNode {
    public WhileTrueDynamicBlocksNode(final SBlock rcvr, final SBlock arg,
        final Universe universe, final SourceSection source, final boolean executesEnforced) {
      super(rcvr, arg, true, universe, source, executesEnforced);
    }
  }

  public static final class WhileFalseDynamicBlocksNode extends WhileWithDynamicBlocksNode {
    public WhileFalseDynamicBlocksNode(final SBlock rcvr, final SBlock arg,
        final Universe universe, final SourceSection source, final boolean executesEnforced) {
      super(rcvr, arg, false, universe, source, executesEnforced);
    }
  }
}
