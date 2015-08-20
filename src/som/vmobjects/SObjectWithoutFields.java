package som.vmobjects;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.interpreter.objectstorage.ClassFactory;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;


public class SObjectWithoutFields extends SAbstractObject {
  @CompilationFinal protected SClass       clazz;
  @CompilationFinal protected ClassFactory factory;

  public SObjectWithoutFields(final SClass clazz) {
    this.clazz   = clazz;
    this.factory = clazz.getClassFactory();
  }

  public SObjectWithoutFields() { }

  @Override
  public final SClass getSOMClass() {
    return clazz;
  }

  public final ClassFactory getFactory() {
    return factory;
  }

  @Override
  public boolean isValue() {
    return clazz.declaredAsValue();
  }

  public void setClass(final SClass value) {
    transferToInterpreterAndInvalidate("SObjectWithoutFields.setClass");
    assert value != null;
    clazz   = value;
    factory = value.getClassFactory();
  }
}
