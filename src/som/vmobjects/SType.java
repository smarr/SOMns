package som.vmobjects;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import som.vm.SomStructuralType;


public abstract class SType extends SObjectWithClass {
  @CompilationFinal public static SClass typeClass;

  public static void setSOMClass(final SClass cls) {
    typeClass = cls;
  }

  public SType() {
    super(typeClass, typeClass.getInstanceFactory());
  }

  @Override
  public boolean isValue() {
    return true;
  }

  public abstract SSymbol[] getSignatures();

  public abstract boolean isSuperTypeOf(final SomStructuralType other);

  public class InterfaceType extends SType {
    @CompilationFinal(dimensions = 1) public final SSymbol[] signatures;

    public InterfaceType(final SSymbol[] signatures) {
      this.signatures = signatures;
    }

    @Override
    public boolean isSuperTypeOf(final SomStructuralType other) {
      for (SSymbol sigThis : signatures) {
        boolean found = false;
        for (SSymbol sigOther : other.signatures) {
          if (sigThis == sigOther) {
            found = true;
            break;
          }
        }
        if (!found) {
          return false;
        }
      }
      return true;
    }

    @Override
    public SSymbol[] getSignatures() {
      return signatures;
    }
  }
}
