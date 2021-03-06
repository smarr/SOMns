package som.vmobjects;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;

import som.interpreter.objectstorage.ClassFactory;


@ExportLibrary(InteropLibrary.class)
public abstract class SObjectWithClass extends SAbstractObject implements SObjectWithContext {
  @CompilationFinal protected SClass       clazz;
  @CompilationFinal protected ClassFactory classGroup; // the factory by which clazz was
                                                       // created

  public SObjectWithClass(final SClass clazz, final ClassFactory classGroup) {
    this.clazz = clazz;
    this.classGroup = classGroup;
    assert clazz.getInstanceFactory() == classGroup;
  }

  public SObjectWithClass() {}

  /** Copy Constructor. */
  protected SObjectWithClass(final SObjectWithClass old) {
    this.clazz = old.clazz;
    this.classGroup = old.classGroup;
  }

  @Override
  public final SClass getSOMClass() {
    return clazz;
  }

  public final ClassFactory getFactory() {
    assert classGroup != null;
    return classGroup;
  }

  public void setClass(final SClass value) {
    CompilerAsserts.neverPartOfCompilation(
        "Only meant to be used in object system initalization");
    assert value != null;
    clazz = value;
    classGroup = value.getInstanceFactory();
    // assert classGroup != null || !ObjectSystem.isInitialized();
  }

  public void setClassGroup(final ClassFactory factory) {
    classGroup = factory;
    assert factory.getClassName() == clazz.getName();
  }

  @Override
  public MaterializedFrame getContext() {
    return clazz.getContext();
  }

  public static final class SObjectWithoutFields extends SObjectWithClass {
    public SObjectWithoutFields(final SClass clazz, final ClassFactory factory) {
      super(clazz, factory);
    }

    public SObjectWithoutFields() {
      super();
    }

    public SObjectWithoutFields(final SObjectWithoutFields old) {
      super(old);
    }

    public SObjectWithoutFields cloneBasics() {
      return new SObjectWithoutFields(this);
    }

    @Override
    public boolean isValue() {
      return clazz.declaredAsValue();
    }
  }
}
