package som.interpreter.nodes.specialized;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.BinaryBasicOperation;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import tools.dym.Tags.ControlFlowCondition;
import tools.dym.Tags.OpComparison;


@GenerateNodeFactory
public abstract class AndMessageNode extends BinaryComplexOperation {

  private final SInvokable blockMethod;
  @Child private DirectCallNode blockValueSend;

  public AndMessageNode(final SBlock arg, final SourceSection source) {
    super(false, source);
    blockMethod = arg.getMethod();
    blockValueSend = Truffle.getRuntime().createDirectCallNode(
        blockMethod.getCallTarget());
  }

  public AndMessageNode(final AndMessageNode copy) {
    super(false, copy.getSourceSection());
    blockMethod    = copy.blockMethod;
    blockValueSend = copy.blockValueSend;
  }

  protected final boolean isSameBlock(final SBlock argument) {
    return argument.getMethod() == blockMethod;
  }

  @Override
  protected boolean isTaggedWith(final Class<?> tag) {
    if (tag == ControlFlowCondition.class) {
      return true;
    } else if (tag == OpComparison.class) {
      return true;
    } else {
      return super.isTaggedWith(tag);
    }
  }

  @Specialization(guards = "isSameBlock(argument)")
  public final boolean doAnd(final VirtualFrame frame, final boolean receiver,
      final SBlock argument) {
    if (receiver == false) {
      return false;
    } else {
      return (boolean) blockValueSend.call(frame, new Object[] {argument});
    }
  }

  @GenerateNodeFactory
  public abstract static class AndBoolMessageNode extends BinaryBasicOperation {
    public AndBoolMessageNode(final SourceSection source) { super(false, source); }

    @Override
    protected boolean isTaggedWith(final Class<?> tag) {
      if (tag == OpComparison.class) {
        return true;
      } else {
        return super.isTaggedWith(tag);
      }
    }

    @Specialization
    public final boolean doAnd(final VirtualFrame frame, final boolean receiver, final boolean argument) {
      return receiver && argument;
    }
  }
}
