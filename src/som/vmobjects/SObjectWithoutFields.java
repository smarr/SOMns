package som.vmobjects;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;


public class SObjectWithoutFields extends SAbstractObject {
  @CompilationFinal protected SClass clazz;

  public SObjectWithoutFields(final SClass clazz) {
    this.clazz = clazz;
  }

  public SObjectWithoutFields() { }

  @Override
  public final SClass getSOMClass() {
    return clazz;
  }

  @Override
  public boolean isValue() {
    return clazz.declaredAsValue();
  }

  public void setClass(final SClass value) {
    transferToInterpreterAndInvalidate("SObjectWithoutFields.setClass");
    assert value != null;
    clazz = value;
  }
}
