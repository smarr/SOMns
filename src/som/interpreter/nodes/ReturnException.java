/**
 * 
 */
package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;

import som.vmobjects.Object;

// TODO: rename to ReturnNonLocalException
public final class ReturnException extends ControlFlowException {
  
  final private Object result;
  final private VirtualFrame target;
  
  public ReturnException(final Object result, final VirtualFrame target) {
    this.result = result;
    this.target = target;
  }
  
  public Object result() {
    return result;
  }
  
  public boolean reachedTarget(VirtualFrame current) {
    return current == target;
  }

  private static final long serialVersionUID = 8003954137724716L;

}
