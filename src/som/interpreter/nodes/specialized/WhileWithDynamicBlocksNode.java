package som.interpreter.nodes.specialized;

import som.vm.NotYetImplementedException;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;


public abstract class WhileWithDynamicBlocksNode extends AbstractWhileNode {
  private final SInvokable conditionMethod;
  private final SInvokable bodyMethod;


  private WhileWithDynamicBlocksNode(final SBlock rcvr, final SBlock arg,
      final boolean predicateBool, final SourceSection source) {
    super(rcvr, arg, predicateBool, source);
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
        final SourceSection source) {
      super(rcvr, arg, true, source);
    }
  }

  public static final class WhileFalseDynamicBlocksNode extends WhileWithDynamicBlocksNode {
    public WhileFalseDynamicBlocksNode(final SBlock rcvr, final SBlock arg,
        final SourceSection source) {
      super(rcvr, arg, false, source);
    }
  }
}
