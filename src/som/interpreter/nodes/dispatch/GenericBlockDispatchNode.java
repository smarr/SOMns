package som.interpreter.nodes.dispatch;

import som.vmobjects.SBlock;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;


public final class GenericBlockDispatchNode extends AbstractDispatchNode {
  @Child private IndirectCallNode call = Truffle.getRuntime().createIndirectCallNode();

  @Override
  public Object executeDispatch(final VirtualFrame frame,
      final Object[] arguments) {
    SBlock rcvr = (SBlock) arguments[0];
    return call.call(frame, rcvr.getMethod().getCallTarget(), arguments);
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1000;
  }
}
