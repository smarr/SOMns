package som.interpreter.nodes.specialized;

import som.interpreter.SArguments;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;


public abstract class AndMessageNode extends BinaryExpressionNode {

  private final SInvokable blockMethod;
  @Child private DirectCallNode blockValueSend;
  private final boolean    blockEnforced;

  public AndMessageNode(final SBlock arg, final SourceSection source,
      final boolean executesEnforced) {
    super(source, executesEnforced);
    blockMethod = arg.getMethod();
    blockValueSend = Truffle.getRuntime().createDirectCallNode(
        blockMethod.getCallTarget(arg.isEnforced() || executesEnforced));
    blockEnforced = arg.isEnforced();
  }

  public AndMessageNode(final AndMessageNode copy) {
    super(copy.getSourceSection(), copy.executesEnforced);
    blockMethod    = copy.blockMethod;
    blockValueSend = copy.blockValueSend;
    blockEnforced  = copy.blockEnforced;
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
      SObject domain = SArguments.domain(frame);
      return (boolean) blockValueSend.call(frame,
          SArguments.createSArguments(domain, blockEnforced,
              new Object[] {argument}));
    }
  }

  public abstract static class AndBoolMessageNode extends BinaryExpressionNode {

    public AndBoolMessageNode(final SourceSection source,
        final boolean executesEnforced) {
      super(source, executesEnforced);
    }

    public AndBoolMessageNode(final AndBoolMessageNode node) {
      this(node.getSourceSection(), node.executesEnforced);
    }

    @Specialization
    public final boolean doAnd(final VirtualFrame frame, final boolean receiver, final boolean argument) {
      return receiver && argument;
    }
  }
}
