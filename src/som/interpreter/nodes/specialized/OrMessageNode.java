package som.interpreter.nodes.specialized;

import som.interpreter.SArguments;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;


public abstract class OrMessageNode extends BinaryExpressionNode {
  private final SInvokable blockMethod;
  @Child private DirectCallNode blockValueSend;

  public OrMessageNode(final SBlock arg, final SourceSection source) {
    super(source, false);   // TODO: enforced!!!
    blockMethod = arg.getMethod();
    blockValueSend = Truffle.getRuntime().createDirectCallNode(
        blockMethod.getCallTarget());
  }

  public OrMessageNode(final OrMessageNode copy) {
    super(copy.getSourceSection(), false);   // TODO: enforced!!!
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
      SObject domain = SArguments.domain(frame);
      boolean enforced = SArguments.enforced(frame);
      return (boolean) blockValueSend.call(frame, SArguments.createSArguments(domain, enforced, new Object[] {argument}));
    }
  }

  public abstract static class OrBoolMessageNode extends BinaryExpressionNode {
    public OrBoolMessageNode(final SourceSection source) { super(source, false); /* TODO: enforced!!! */ }
    @Specialization
    public final boolean doOr(final VirtualFrame frame, final boolean receiver,
        final boolean argument) {
      return receiver || argument;
    }
  }
}
