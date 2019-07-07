package som.vmobjects;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import som.interop.SomInteropObject;
import som.vm.constants.Nil;


@ExportLibrary(InteropLibrary.class)
public abstract class SAbstractObject implements SomInteropObject {

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

  @ExportMessage
  public final boolean isNull() {
    return this == Nil.nilObject;
  }
}
