/**
 * 
 */
package som.interpreter.nodes;

import com.oracle.truffle.api.nodes.ControlFlowException;

import som.vmobjects.Object;

public final class ReturnException extends ControlFlowException {
  
  final private Object value;
  
  public ReturnException(Object value) {
    this.value = value;
  }
  
  public Object value() {
    return value;
  }

  private static final long serialVersionUID = 8003954137724716L;

}
