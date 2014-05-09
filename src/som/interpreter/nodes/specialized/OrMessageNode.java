package som.interpreter.nodes.specialized;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;


public abstract class OrMessageNode extends BinaryExpressionNode {
  private final SInvokable blockMethod;
  @Child private DirectCallNode blockValueSend;

  public OrMessageNode(final SBlock arg) {
    blockMethod = arg.getMethod();
    blockValueSend = Truffle.getRuntime().createDirectCallNode(
        blockMethod.getCallTarget());
  }

  public OrMessageNode(final OrMessageNode copy) {
    blockMethod = copy.blockMethod;
    blockValueSend = copy.blockValueSend;
  }

  protected final boolean isSameBlock(final boolean receiver, final SBlock argument) {
    return argument.getMethod() == blockMethod;
  }

  @Specialization(guards = "isSameBlock")
  public final boolean doOr(final VirtualFrame frame, final boolean receiver,
      final SBlock argument) {
    if (receiver) {
      return true;
    } else {
      return (boolean) blockValueSend.call(frame, new Object[] {argument});
    }
  }

  public abstract static class OrBoolMessageNode extends BinaryExpressionNode {
    @Specialization
    public final boolean doOr(final VirtualFrame frame, final boolean receiver,
        final boolean argument) {
      return receiver || argument;
    }
  }
}
