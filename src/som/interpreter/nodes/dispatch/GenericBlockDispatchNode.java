package som.interpreter.nodes.dispatch;

import som.vmobjects.SBlock;

import com.oracle.truffle.api.frame.VirtualFrame;


public final class GenericBlockDispatchNode extends AbstractDispatchNode {

  @Override
  public Object executeDispatch(final VirtualFrame frame,
      final Object[] arguments) {
    SBlock rcvr = (SBlock) arguments[0];
    return rcvr.getMethod().invoke(arguments);
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1000;
  }
}
