package som.vm.constants;

import som.vm.ObjectSystem;
import som.vmobjects.SClass;


public final class Classes {
  public static final SClass  topClass;
  public static final SClass  thingClass;
  public static final SClass  objectClass;
  public static final SClass  valueClass;
  public static final SClass  transferClass;
  public static final SClass  classClass;
  public static final SClass  metaclassClass;

  public static final SClass  nilClass;
  public static final SClass  integerClass;
  public static final SClass  arrayReadMixinClass;
  public static final SClass  arrayClass;
  public static final SClass  valueArrayClass;
  public static final SClass  transferArrayClass;
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
    metaclassClass = ObjectSystem.newMetaclassClass(KernelObj.kernel);
    classClass     = new SClass(KernelObj.kernel);
    SClass classClassClass = new SClass(KernelObj.kernel);
    ObjectSystem.initializeClassAndItsClass("Class", classClass, classClassClass);

    // Allocate the rest of the system classes
    topClass        = ObjectSystem.newEmptyClassWithItsClass("Top");
    thingClass      = ObjectSystem.newEmptyClassWithItsClass("Thing");
    objectClass     = ObjectSystem.newEmptyClassWithItsClass("Object");
    valueClass      = ObjectSystem.newEmptyClassWithItsClass("Value");
    transferClass   = ObjectSystem.newEmptyClassWithItsClass("TransferObject");
    nilClass        = ObjectSystem.newEmptyClassWithItsClass("Nil");
    arrayReadMixinClass = ObjectSystem.newEmptyClassWithItsClass("ArrayReadMixin");
    arrayClass      = ObjectSystem.newEmptyClassWithItsClass("Array");
    valueArrayClass = ObjectSystem.newEmptyClassWithItsClass("ValueArray");
    transferArrayClass = ObjectSystem.newEmptyClassWithItsClass("TransferArray");
    symbolClass     = ObjectSystem.newEmptyClassWithItsClass("Symbol");
    methodClass     = ObjectSystem.newEmptyClassWithItsClass("Method");
    integerClass    = ObjectSystem.newEmptyClassWithItsClass("Integer");
    stringClass     = ObjectSystem.newEmptyClassWithItsClass("String");
    doubleClass     = ObjectSystem.newEmptyClassWithItsClass("Double");

    booleanClass = ObjectSystem.newEmptyClassWithItsClass("Boolean");
    trueClass    = ObjectSystem.newEmptyClassWithItsClass("True");
    falseClass   = ObjectSystem.newEmptyClassWithItsClass("False");

    blockClass  = ObjectSystem.newEmptyClassWithItsClass("Block");
    blockClass1 = ObjectSystem.newEmptyClassWithItsClass("Block1");
    blockClass2 = ObjectSystem.newEmptyClassWithItsClass("Block2");
    blockClass3 = ObjectSystem.newEmptyClassWithItsClass("Block3");
  }
}
