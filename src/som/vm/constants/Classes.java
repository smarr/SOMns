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
    metaclassClass.setSerializer(ClassSerializationNode.create());
    classClass = new SClass(KernelObj.kernel);
    classClass.setSerializer(ClassSerializationNode.create());
    SClass classClassClass = new SClass(KernelObj.kernel);
    ObjectSystem.initializeClassAndItsClass("Class", classClass, classClassClass);

    // Allocate the rest of the system classes

    topClass = ObjectSystem.newEmptyClassWithItsClass("Top");
    topClass.setSerializer(new SObjectWithoutFieldsSerializationNode(topClass));
    thingClass = ObjectSystem.newEmptyClassWithItsClass("Thing");
    thingClass.setSerializer(new SObjectWithoutFieldsSerializationNode(thingClass));
    objectClass = ObjectSystem.newEmptyClassWithItsClass("Object");
    objectClass.setSerializer(
        new SObjectWithoutFieldsSerializationNode(objectClass));
    valueClass = ObjectSystem.newEmptyClassWithItsClass("Value");
    valueClass.setSerializer(new SObjectWithoutFieldsSerializationNode(valueClass));
    transferClass = ObjectSystem.newEmptyClassWithItsClass("TransferObject");
    transferClass.setSerializer(
        new SObjectWithoutFieldsSerializationNode(transferClass));
    nilClass = ObjectSystem.newEmptyClassWithItsClass("Nil");
    nilClass.setSerializer(new NilSerializationNode());

    arrayReadMixinClass = ObjectSystem.newEmptyClassWithItsClass("ArrayReadMixin");
    arrayReadMixinClass.setSerializer(
        new SObjectWithoutFieldsSerializationNode(arrayReadMixinClass));
    arrayClass = ObjectSystem.newEmptyClassWithItsClass("Array");
    arrayClass.setSerializer(ArraySerializationNode.create());
    valueArrayClass = ObjectSystem.newEmptyClassWithItsClass("ValueArray");
    valueArrayClass.setSerializer(ValueArraySerializationNode.create());
    transferArrayClass = ObjectSystem.newEmptyClassWithItsClass("TransferArray");
    transferArrayClass.setSerializer(TransferArraySerializationNode.create());
    symbolClass = ObjectSystem.newEmptyClassWithItsClass("Symbol");
    symbolClass.setSerializer(new SymbolSerializationNode());

    methodClass = ObjectSystem.newEmptyClassWithItsClass("Method");
    methodClass.setSerializer(new SInvokableSerializationNode());

    integerClass = ObjectSystem.newEmptyClassWithItsClass("Integer");
    integerClass.setSerializer(new IntegerSerializationNode());
    stringClass = ObjectSystem.newEmptyClassWithItsClass("String");
    stringClass.setSerializer(new StringSerializationNode());
    doubleClass = ObjectSystem.newEmptyClassWithItsClass("Double");
    doubleClass.setSerializer(new DoubleSerializationNode());

    booleanClass = ObjectSystem.newEmptyClassWithItsClass("Boolean");
    booleanClass.setSerializer(new BooleanSerializationNode());
    trueClass = ObjectSystem.newEmptyClassWithItsClass("True");
    trueClass.setSerializer(new TrueSerializationNode());
    falseClass = ObjectSystem.newEmptyClassWithItsClass("False");
    falseClass.setSerializer(new FalseSerializationNode());

    blockClass = ObjectSystem.newEmptyClassWithItsClass("Block");
    blockClass.setSerializer(new BlockSerializationNode());
  }
}
