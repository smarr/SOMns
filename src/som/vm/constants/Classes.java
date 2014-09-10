package som.vm.constants;

import som.vm.Universe;
import som.vmobjects.SClass;


public final class Classes {
  public static final SClass  objectClass;
  public static final SClass  classClass;
  public static final SClass  metaclassClass;

  public static final SClass  nilClass;
  public static final SClass  integerClass;
  public static final SClass  arrayClass;
  public static final SClass  methodClass;
  public static final SClass  symbolClass;
  public static final SClass  primitiveClass;
  public static final SClass  stringClass;
  public static final SClass  doubleClass;

  public static final SClass  booleanClass;

  // These classes can be statically preinitialized.
  static {
    // Allocate the Metaclass classes
    metaclassClass = Universe.newMetaclassClass();

    // Allocate the rest of the system classes

    objectClass     = Universe.newSystemClass();
    nilClass        = Universe.newSystemClass();
    classClass      = Universe.newSystemClass();
    arrayClass      = Universe.newSystemClass();
    symbolClass     = Universe.newSystemClass();
    methodClass     = Universe.newSystemClass();
    integerClass    = Universe.newSystemClass();
    primitiveClass  = Universe.newSystemClass();
    stringClass     = Universe.newSystemClass();
    doubleClass     = Universe.newSystemClass();
    booleanClass    = Universe.newSystemClass();
  }
}
