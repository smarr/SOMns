package som.interpreter.nodes.dispatch;

import som.interpreter.SArguments;
import som.vmobjects.SBlock;
import som.vmobjects.SObject;

import com.oracle.truffle.api.frame.VirtualFrame;


public final class GenericBlockDispatchNode extends AbstractDispatchNode {

  public GenericBlockDispatchNode() {
    super(false); // is not used
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame,
      final Object[] arguments) {
    SBlock rcvr = (SBlock) arguments[0];
    SObject domain = SArguments.domain(frame);
    return rcvr.getMethod().invoke(domain, rcvr.isEnforced(), arguments);
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1000;
  }
}
