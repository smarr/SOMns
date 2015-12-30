package som.vmobjects;

import com.oracle.truffle.api.CompilerAsserts;


public abstract class SAbstractObject {

  public abstract SClass getSOMClass();
  public abstract boolean isValue();

  @Override
  public String toString() {
    CompilerAsserts.neverPartOfCompilation();
    SClass clazz = getSOMClass();
    if (clazz == null) {
      return "an Object(clazz==null)";
    }
    return "a " + clazz.getName().getString();
  }
}
