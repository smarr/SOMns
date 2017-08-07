package som.interpreter;

import com.oracle.truffle.api.nodes.ControlFlowException;

import som.vmobjects.SAbstractObject;


public final class SomException extends ControlFlowException {

  private static final long     serialVersionUID = -639789248178270606L;
  private final SAbstractObject somObj;

  public SomException(final SAbstractObject somObj) {
    this.somObj = somObj;
  }

  public SAbstractObject getSomObject() {
    return somObj;
  }

  @Override
  public String toString() {
    return "SomException[" + somObj.toString() + "]";
  }
}
