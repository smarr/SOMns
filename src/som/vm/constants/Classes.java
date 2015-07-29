package som.vm.constants;

import som.vm.Bootstrap;
import som.vmobjects.SClass;


public final class Classes {
  public static final SClass  topClass;
  public static final SClass  thingClass;
  public static final SClass  objectClass;
  public static final SClass  valueClass;
  public static final SClass  classClass;
  public static final SClass  metaclassClass;

  public static final SClass  nilClass;
  public static final SClass  integerClass;
  public static final SClass  arrayClass;
  public static final SClass  methodClass;
  public static final SClass  symbolClass;
  public static final SClass  stringClass;
  public static final SClass  doubleClass;

  public static final SClass booleanClass;
  public static final SClass trueClass;
  public static final SClass falseClass;

  public static final SClass blockClass;
  public static final SClass blockClass1;
  public static final SClass blockClass2;
  public static final SClass blockClass3;

  // These classes can be statically preinitialized.
  static {
    // Allocate the Metaclass classes
    metaclassClass = Bootstrap.newMetaclassClass(KernelObj.kernel);
    classClass     = new SClass(KernelObj.kernel);
    SClass classClassClass = new SClass(KernelObj.kernel);
    Bootstrap.initializeClassAndItsClass("Class", classClass, classClassClass);
    classClass.setInstancesAreValues(true);

    // Allocate the rest of the system classes
    topClass        = Bootstrap.newEmptyClassWithItsClass("Top");
    thingClass      = Bootstrap.newEmptyClassWithItsClass("Thing");
    objectClass     = Bootstrap.newEmptyClassWithItsClass("Object");
    valueClass      = Bootstrap.newEmptyClassWithItsClass("Value");
    nilClass        = Bootstrap.newEmptyClassWithItsClass("Nil");
    arrayClass      = Bootstrap.newEmptyClassWithItsClass("Array");
    symbolClass     = Bootstrap.newEmptyClassWithItsClass("Symbol");
    methodClass     = Bootstrap.newEmptyClassWithItsClass("Method");
    integerClass    = Bootstrap.newEmptyClassWithItsClass("Integer");
    stringClass     = Bootstrap.newEmptyClassWithItsClass("String");
    doubleClass     = Bootstrap.newEmptyClassWithItsClass("Double");

    booleanClass = Bootstrap.newEmptyClassWithItsClass("Boolean");
    trueClass    = Bootstrap.newEmptyClassWithItsClass("True");
    falseClass   = Bootstrap.newEmptyClassWithItsClass("False");

    blockClass  = Bootstrap.newEmptyClassWithItsClass("Block");
    blockClass1 = Bootstrap.newEmptyClassWithItsClass("Block1");
    blockClass2 = Bootstrap.newEmptyClassWithItsClass("Block2");
    blockClass3 = Bootstrap.newEmptyClassWithItsClass("Block3");
  }
}
