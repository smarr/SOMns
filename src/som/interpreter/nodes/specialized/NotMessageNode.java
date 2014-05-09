package som.interpreter.nodes.specialized;

import som.interpreter.nodes.nary.UnaryExpressionNode;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class NotMessageNode extends UnaryExpressionNode {
  @Specialization
  public final boolean doNot(final VirtualFrame frame, final boolean receiver) {
    return !receiver;
  }
}
