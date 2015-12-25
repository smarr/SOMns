package som.interpreter;

import som.vmobjects.SAbstractObject;


public final class SomException extends RuntimeException {

  private static final long serialVersionUID = -639789248178270606L;
  private final SAbstractObject somObj;

  public SomException(final SAbstractObject somObj) {
      /*
       * We use the super constructor that initializes the cause to null.
       * Without that, the cause would be this exception itself. This helps
       * escape analysis: it avoids the circle of an object pointing to itself.
       */
      super((Throwable) null);
      this.somObj = somObj;
  }

  public SAbstractObject getSomObject() {
    return somObj;
  }

  /**
   * For performance reasons, this exception does not record any stack trace
   * information.
   *
   * TODO: at some point, fill in a SOMns stack...
   */
  @SuppressWarnings("sync-override")
  @Override
  public Throwable fillInStackTrace() {
      return null;
  }

  @Override
  public String toString() {
    return "SomException[" + somObj.toString() + "]";
  }
}
