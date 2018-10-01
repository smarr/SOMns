package som.vm.constants;

import som.vm.ObjectSystem;
import som.vmobjects.SClass;
import tools.snapshot.nodes.AbstractArraySerializationNodeGen.ArraySerializationNodeFactory;
import tools.snapshot.nodes.AbstractArraySerializationNodeGen.TransferArraySerializationNodeFactory;
import tools.snapshot.nodes.AbstractArraySerializationNodeGen.ValueArraySerializationNodeFactory;
import tools.snapshot.nodes.BlockSerializationNodeFactory;
import tools.snapshot.nodes.ObjectSerializationNodesFactory.SObjectWithoutFieldsSerializationNodeFactory;
import tools.snapshot.nodes.PrimitiveSerializationNodesFactory.BooleanSerializationNodeFactory;
import tools.snapshot.nodes.PrimitiveSerializationNodesFactory.ClassSerializationNodeFactory;
import tools.snapshot.nodes.PrimitiveSerializationNodesFactory.DoubleSerializationNodeFactory;
import tools.snapshot.nodes.PrimitiveSerializationNodesFactory.FalseSerializationNodeFactory;
import tools.snapshot.nodes.PrimitiveSerializationNodesFactory.IntegerSerializationNodeFactory;
import tools.snapshot.nodes.PrimitiveSerializationNodesFactory.NilSerializationNodeFactory;
import tools.snapshot.nodes.PrimitiveSerializationNodesFactory.SInvokableSerializationNodeFactory;
import tools.snapshot.nodes.PrimitiveSerializationNodesFactory.StringSerializationNodeFactory;
import tools.snapshot.nodes.PrimitiveSerializationNodesFactory.SymbolSerializationNodeFactory;
import tools.snapshot.nodes.PrimitiveSerializationNodesFactory.TrueSerializationNodeFactory;


public final class Classes {
  public static final SClass topClass;
  public static final SClass thingClass;
  public static final SClass objectClass;
  public static final SClass valueClass;
  public static final SClass transferClass;
  public static final SClass classClass;
  public static final SClass metaclassClass;

  public static final SClass nilClass;
  public static final SClass integerClass;
  public static final SClass arrayReadMixinClass;
  public static final SClass arrayClass;
  public static final SClass valueArrayClass;
  public static final SClass transferArrayClass;
  public static final SClass methodClass;
  public static final SClass symbolClass;
  public static final SClass stringClass;
  public static final SClass doubleClass;

  public static final SClass booleanClass;
  public static final SClass trueClass;
  public static final SClass falseClass;

  public static final SClass blockClass;

  // These classes can be statically preinitialized.
  static {
    // Allocate the Metaclass classes
    metaclassClass = ObjectSystem.newMetaclassClass(KernelObj.kernel);
    classClass = new SClass(KernelObj.kernel, ClassSerializationNodeFactory.getInstance());
    SClass classClassClass = new SClass(KernelObj.kernel);
    ObjectSystem.initializeClassAndItsClass("Class", classClass, classClassClass);

    // Allocate the rest of the system classes

    topClass = ObjectSystem.newEmptyClassWithItsClass("Top",
        SObjectWithoutFieldsSerializationNodeFactory.getInstance());
    thingClass = ObjectSystem.newEmptyClassWithItsClass("Thing",
        SObjectWithoutFieldsSerializationNodeFactory.getInstance());
    objectClass = ObjectSystem.newEmptyClassWithItsClass("Object",
        SObjectWithoutFieldsSerializationNodeFactory.getInstance());
    valueClass = ObjectSystem.newEmptyClassWithItsClass("Value",
        SObjectWithoutFieldsSerializationNodeFactory.getInstance());
    transferClass = ObjectSystem.newEmptyClassWithItsClass("TransferObject",
        SObjectWithoutFieldsSerializationNodeFactory.getInstance());
    nilClass = ObjectSystem.newEmptyClassWithItsClass("Nil",
        NilSerializationNodeFactory.getInstance());

    arrayReadMixinClass = ObjectSystem.newEmptyClassWithItsClass("ArrayReadMixin",
        SObjectWithoutFieldsSerializationNodeFactory.getInstance());
    arrayClass = ObjectSystem.newEmptyClassWithItsClass("Array",
        ArraySerializationNodeFactory.getInstance());
    valueArrayClass = ObjectSystem.newEmptyClassWithItsClass("ValueArray",
        ValueArraySerializationNodeFactory.getInstance());
    transferArrayClass = ObjectSystem.newEmptyClassWithItsClass("TransferArray",
        TransferArraySerializationNodeFactory.getInstance());
    symbolClass = ObjectSystem.newEmptyClassWithItsClass("Symbol",
        SymbolSerializationNodeFactory.getInstance());
    methodClass = ObjectSystem.newEmptyClassWithItsClass("Method",
        SInvokableSerializationNodeFactory.getInstance());

    integerClass = ObjectSystem.newEmptyClassWithItsClass("Integer",
        IntegerSerializationNodeFactory.getInstance());
    stringClass = ObjectSystem.newEmptyClassWithItsClass("String",
        StringSerializationNodeFactory.getInstance());
    doubleClass = ObjectSystem.newEmptyClassWithItsClass("Double",
        DoubleSerializationNodeFactory.getInstance());

    booleanClass = ObjectSystem.newEmptyClassWithItsClass("Boolean",
        BooleanSerializationNodeFactory.getInstance());
    trueClass = ObjectSystem.newEmptyClassWithItsClass("True",
        TrueSerializationNodeFactory.getInstance());
    falseClass = ObjectSystem.newEmptyClassWithItsClass("False",
        FalseSerializationNodeFactory.getInstance());

    blockClass = ObjectSystem.newEmptyClassWithItsClass("Block",
        BlockSerializationNodeFactory.getInstance());
  }
}
