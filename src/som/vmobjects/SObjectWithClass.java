package som.vmobjects;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.interpreter.objectstorage.ClassFactory;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;


public abstract class SObjectWithClass extends SAbstractObject {
  @CompilationFinal protected SClass       clazz;
  @CompilationFinal protected ClassFactory factory;

  public SObjectWithClass(final SClass clazz) {
    this.clazz   = clazz;
    this.factory = clazz.getClassFactory();
  }

  public SObjectWithClass() { }

  @Override
  public final SClass getSOMClass() {
    return clazz;
  }

  public final ClassFactory getFactory() {
    return factory;
  }

  public void setClass(final SClass value) {
    transferToInterpreterAndInvalidate("SObjectWithoutFields.setClass");
    assert value != null;
    clazz   = value;
    factory = value.getClassFactory();
  }

  public static final class SObjectWithoutFields extends SObjectWithClass {
    public SObjectWithoutFields(final SClass clazz) {
      super(clazz);
    }

    public SObjectWithoutFields() { super(); }

    @Override
    public boolean isValue() {
      return clazz.declaredAsValue();
    }
  }
}
