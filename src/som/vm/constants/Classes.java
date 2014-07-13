package som.vm.constants;

import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SDomain;


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

  public static final SClass  domainClass;

  public static final SClass  booleanClass;

  // These classes can be statically preinitialized.
  static {
    // Allocate the Metaclass classes
    metaclassClass = Universe.newMetaclassClass(Domain.standard);

    // Allocate the rest of the system classes

    objectClass     = Universe.newSystemClass(Domain.standard);
    nilClass        = Universe.newSystemClass(Domain.standard);
    classClass      = Universe.newSystemClass(Domain.standard);
    arrayClass      = Universe.newSystemClass(Domain.standard);
    symbolClass     = Universe.newSystemClass(Domain.standard);
    methodClass     = Universe.newSystemClass(Domain.standard);
    integerClass    = Universe.newSystemClass(Domain.standard);
    primitiveClass  = Universe.newSystemClass(Domain.standard);
    stringClass     = Universe.newSystemClass(Domain.standard);
    doubleClass     = Universe.newSystemClass(Domain.standard, SDomain.NUM_SDOMAIN_FIELDS);

    domainClass     = Universe.newSystemClass(Domain.standard);

    booleanClass    = Universe.newSystemClass(Domain.standard);
  }
}
