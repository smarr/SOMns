package som.interpreter.nodes.specialized;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;


public abstract class AndMessageNode extends BinaryExpressionNode {

  private final SInvokable blockMethod;
  @Child private DirectCallNode blockValueSend;

  public AndMessageNode(final SBlock arg, final SourceSection source) {
    super(source, false);  // TODO: enforced!!!
    blockMethod = arg.getMethod();
    blockValueSend = Truffle.getRuntime().createDirectCallNode(
        blockMethod.getCallTarget());
  }

  public AndMessageNode(final AndMessageNode copy) {
    super(copy.getSourceSection(), false);  // TODO: enforced!!!
    blockMethod    = copy.blockMethod;
    blockValueSend = copy.blockValueSend;
  }

  protected final boolean isSameBlock(final boolean receiver, final SBlock argument) {
    return argument.getMethod() == blockMethod;
  }

  @Specialization(guards = "isSameBlock")
  public final boolean doAnd(final VirtualFrame frame, final boolean receiver,
      final SBlock argument) {
    if (receiver == false) {
      return false;
    } else {
      return (boolean) blockValueSend.call(frame, new Object[] {argument});
    }
  }

  public abstract static class AndBoolMessageNode extends BinaryExpressionNode {

    public AndBoolMessageNode(final SourceSection source) {
      super(source, false);   // TODO: enforced!!!
    }

    @Specialization
    public final boolean doAnd(final VirtualFrame frame, final boolean receiver, final boolean argument) {
      return receiver && argument;
    }
  }
}
