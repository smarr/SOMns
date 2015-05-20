package som.vmobjects;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;


public class SObjectWithoutFields extends SAbstractObject {
  @CompilationFinal protected SClass clazz;

  public SObjectWithoutFields(final SAbstractObject enclosing,
      final SClass clazz) {
    super(enclosing);
    this.clazz = clazz;
  }

  public SObjectWithoutFields(final SAbstractObject enclosing) {
    super(enclosing);
  }

  @Override
  public final SClass getSOMClass() {
    return clazz;
  }

  public final void setClass(final SClass value) {
    transferToInterpreterAndInvalidate("SObjectWithoutFields.setClass");
    assert value != null;
    clazz = value;
  }
}
