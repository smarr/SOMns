package som.interpreter.nodes.specialized;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;


public abstract class OrMessageNode extends BinaryExpressionNode {
  private final SInvokable blockMethod;
  @Child private DirectCallNode blockValueSend;

  public OrMessageNode(final SBlock arg, final SourceSection source) {
    super(source);
    blockMethod = arg.getMethod();
    blockValueSend = Truffle.getRuntime().createDirectCallNode(
        blockMethod.getCallTargetIfAvailable());
  }

  public OrMessageNode(final OrMessageNode copy) {
    super(copy.getSourceSection());
    blockMethod = copy.blockMethod;
    blockValueSend = copy.blockValueSend;
  }

  protected final boolean isSameBlock(final SBlock argument) {
    return argument.getMethod() == blockMethod;
  }

  @Specialization(guards = "isSameBlock(argument)")
  public final boolean doOr(final VirtualFrame frame, final boolean receiver,
      final SBlock argument) {
    if (receiver) {
      return true;
    } else {
      return (boolean) blockValueSend.call(frame, new Object[] {argument});
    }
  }

  public abstract static class OrBoolMessageNode extends BinaryExpressionNode {
    public OrBoolMessageNode(final SourceSection source) { super(source); }
    @Specialization
    public final boolean doOr(final VirtualFrame frame, final boolean receiver,
        final boolean argument) {
      return receiver || argument;
    }
  }
}
