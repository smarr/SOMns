package som.vm.constants;

import som.vm.ObjectSystem;
import som.vmobjects.SClass;
import tools.snapshot.nodes.AbstractArraySerializationNode.ArraySerializationNode;
import tools.snapshot.nodes.AbstractArraySerializationNode.TransferArraySerializationNode;
import tools.snapshot.nodes.AbstractArraySerializationNode.ValueArraySerializationNode;
import tools.snapshot.nodes.BlockSerializationNode;
import tools.snapshot.nodes.ObjectSerializationNodes.SObjectWithoutFieldsSerializationNode;
import tools.snapshot.nodes.PrimitiveSerializationNodes.BooleanSerializationNode;
import tools.snapshot.nodes.PrimitiveSerializationNodes.ClassSerializationNode;
import tools.snapshot.nodes.PrimitiveSerializationNodes.DoubleSerializationNode;
import tools.snapshot.nodes.PrimitiveSerializationNodes.FalseSerializationNode;
import tools.snapshot.nodes.PrimitiveSerializationNodes.IntegerSerializationNode;
import tools.snapshot.nodes.PrimitiveSerializationNodes.NilSerializationNode;
import tools.snapshot.nodes.PrimitiveSerializationNodes.SInvokableSerializationNode;
import tools.snapshot.nodes.PrimitiveSerializationNodes.StringSerializationNode;
import tools.snapshot.nodes.PrimitiveSerializationNodes.SymbolSerializationNode;
import tools.snapshot.nodes.PrimitiveSerializationNodes.TrueSerializationNode;


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
    classClass = new SClass(KernelObj.kernel, ClassSerializationNode::create);
    SClass classClassClass = new SClass(KernelObj.kernel);
    ObjectSystem.initializeClassAndItsClass("Class", classClass, classClassClass);

    // Allocate the rest of the system classes

    topClass = ObjectSystem.newEmptyClassWithItsClass("Top",
        SObjectWithoutFieldsSerializationNode::create);
    thingClass = ObjectSystem.newEmptyClassWithItsClass("Thing",
        SObjectWithoutFieldsSerializationNode::create);
    objectClass = ObjectSystem.newEmptyClassWithItsClass("Object",
        SObjectWithoutFieldsSerializationNode::create);
    valueClass = ObjectSystem.newEmptyClassWithItsClass("Value",
        SObjectWithoutFieldsSerializationNode::create);
    transferClass = ObjectSystem.newEmptyClassWithItsClass("TransferObject",
        SObjectWithoutFieldsSerializationNode::create);
    nilClass = ObjectSystem.newEmptyClassWithItsClass("Nil", NilSerializationNode::create);

    arrayReadMixinClass = ObjectSystem.newEmptyClassWithItsClass("ArrayReadMixin",
        SObjectWithoutFieldsSerializationNode::create);
    arrayClass =
        ObjectSystem.newEmptyClassWithItsClass("Array", ArraySerializationNode::create);
    valueArrayClass = ObjectSystem.newEmptyClassWithItsClass("ValueArray",
        ValueArraySerializationNode::create);
    transferArrayClass = ObjectSystem.newEmptyClassWithItsClass("TransferArray",
        TransferArraySerializationNode::create);
    symbolClass =
        ObjectSystem.newEmptyClassWithItsClass("Symbol", SymbolSerializationNode::create);
    methodClass =
        ObjectSystem.newEmptyClassWithItsClass("Method", SInvokableSerializationNode::create);

    integerClass =
        ObjectSystem.newEmptyClassWithItsClass("Integer", IntegerSerializationNode::create);
    stringClass =
        ObjectSystem.newEmptyClassWithItsClass("String", StringSerializationNode::create);
    doubleClass =
        ObjectSystem.newEmptyClassWithItsClass("Double", DoubleSerializationNode::create);

    booleanClass =
        ObjectSystem.newEmptyClassWithItsClass("Boolean", BooleanSerializationNode::create);
    trueClass = ObjectSystem.newEmptyClassWithItsClass("True", TrueSerializationNode::create);
    falseClass =
        ObjectSystem.newEmptyClassWithItsClass("False", FalseSerializationNode::create);

    blockClass =
        ObjectSystem.newEmptyClassWithItsClass("Block", BlockSerializationNode::create);
  }
}
