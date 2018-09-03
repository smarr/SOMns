package som.vmobjects;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

import som.interop.SAbstractObjectInteropMessagesForeign;
import som.interop.SomInteropObject;


public abstract class SAbstractObject implements SomInteropObject {

  public abstract SClass getSOMClass();

  public abstract boolean isValue();

  @Override
  public ForeignAccess getForeignAccess() {
    return SAbstractObjectInteropMessagesForeign.ACCESS;
  }

  @Override
  public String toString() {
    CompilerAsserts.neverPartOfCompilation();
    SClass clazz = getSOMClass();
    if (clazz == null) {
      return "an Object(clazz==null)";
    }
    return "a " + clazz.getName().getString();
  }

  /**
   * Used by Truffle interop.
   */
  public static boolean isInstance(final TruffleObject obj) {
    return obj instanceof SAbstractObject;
  }
}
