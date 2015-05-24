package som.vm;

import static som.vm.constants.Classes.metaclassClass;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import som.compiler.AccessModifier;
import som.compiler.ClassBuilder.ClassDefinitionId;
import som.compiler.ClassDefinition;
import som.compiler.ClassDefinition.SlotDefinition;
import som.compiler.MethodBuilder;
import som.compiler.SourcecodeCompiler;
import som.interpreter.LexicalScope.ClassScope;
import som.interpreter.Primitive;
import som.interpreter.nodes.ArgumentReadNode.LocalArgumentReadNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.nodes.specialized.AndMessageNodeFactory;
import som.interpreter.nodes.specialized.whileloops.WhilePrimitiveNodeFactory;
import som.primitives.BlockPrimsFactory;
import som.primitives.ClassPrimsFactory;
import som.primitives.DoublePrimsFactory;
import som.primitives.IntegerPrimsFactory;
import som.primitives.LengthPrimFactory;
import som.primitives.MethodPrimsFactory;
import som.primitives.MethodPrimsFactory.InvokeOnPrimFactory;
import som.primitives.ObjectPrimsFactory;
import som.primitives.StringPrimsFactory;
import som.primitives.SystemPrimsFactory;
import som.primitives.arrays.PutAllNodeFactory;
import som.primitives.arrays.ToArgumentsArrayNodeGen;
import som.vm.constants.Classes;
import som.vm.constants.KernelObj;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SInvokable.SMethod;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.dsl.NodeFactory;


public final class Bootstrap {

  private static final Map<String, ClassDefinition> loadedModules = new LinkedHashMap<>();

  @CompilationFinal
  public static ClassDefinition platformModule;
  @CompilationFinal
  public static ClassDefinition kernelModule;

  @CompilationFinal
  public static SClass platformClass;

  public static ClassDefinition loadModule(final String filename)
      throws IOException {
    File file = new File(filename);

    if (loadedModules.containsKey(file.getAbsolutePath())) {
      return loadedModules.get(file.getAbsolutePath());
    }

    ClassDefinition module = SourcecodeCompiler.compileModule(file);
    loadedModules.put(file.getAbsolutePath(), module);

    return module;
  }

  public static void loadPlatformAndKernelModule(final String platformFilename,
      final String kernelFilename) {
    try {
      platformModule = loadModule(platformFilename);
      kernelModule   = loadModule(kernelFilename);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private static SInvokable constructVmMirrorPrimitive(
      final som.primitives.Primitive primitive,
      final NodeFactory<? extends ExpressionNode> factory) {
    CompilerAsserts.neverPartOfCompilation("This is only executed during bootstrapping.");
    SSymbol signature = Symbols.symbolFor(primitive.value());
    assert signature.getNumberOfSignatureArguments() > 1 :
      "Primitives should have the vmMirror as receiver, " +
      "and then at least one object they are applied to";

    // ignore the implicit vmMirror argument
    final int numArgs = signature.getNumberOfSignatureArguments() - 1;

    MethodBuilder prim = new MethodBuilder(true);
    ExpressionNode[] args = new ExpressionNode[numArgs];

    for (int i = 0; i < numArgs; i++) {
      // we do not pass the vmMirror, makes it easier to use the same primitives
      // as replacements on the node level
      args[i] = new LocalArgumentReadNode(i + 1, null);
    }

    ExpressionNode primNode;
    switch (numArgs) {
      case 1:
        primNode = factory.createNode(args[0]);
        break;
      case 2:
        // HACK for node class where we use `executeWith`
        if (factory == PutAllNodeFactory.getInstance()) {
          primNode = factory.createNode(args[0], args[1],
              LengthPrimFactory.create(null));
//        } else if (factory == SpawnWithArgsPrimFactory.getInstance()) {
//          primNode = factory.createNode(args[0], args[1],
//              ToArgumentsArrayNodeGen.create(null, null));
        } else {
          primNode = factory.createNode(args[0], args[1]);
        }
        break;
      case 3:
        // HACK for node class where we use `executeWith`
        if (factory == InvokeOnPrimFactory.getInstance()) {
          primNode = factory.createNode(args[0], args[1], args[2],
              ToArgumentsArrayNodeGen.create(null, null));
        } else {
          primNode = factory.createNode(args[0], args[1], args[2]);
        }
        break;
      case 4:
        primNode = factory.createNode(args[0], args[1], args[2], args[3]);
        break;
      default:
        throw new RuntimeException("Not supported by SOM.");
    }

    Primitive primMethodNode = new Primitive(primNode,
        prim.getCurrentMethodScope().getFrameDescriptor(),
        (ExpressionNode) primNode.deepCopy());
    SInvokable primInvokable = Universe.newMethod(signature,
        AccessModifier.PUBLIC, Symbols.symbolFor(factory.toString()),
        primMethodNode, true, new SMethod[0]);

    return primInvokable;
  }

  private static List<NodeFactory<? extends ExpressionNode>> getFactories() {
    List<NodeFactory<? extends ExpressionNode>> allFactories = new ArrayList<>();
    allFactories.addAll(SystemPrimsFactory.getFactories());
    allFactories.addAll(AndMessageNodeFactory.getFactories());
    allFactories.addAll(WhilePrimitiveNodeFactory.getFactories());
    allFactories.addAll(BlockPrimsFactory.getFactories());
    allFactories.addAll(ClassPrimsFactory.getFactories());
    allFactories.addAll(DoublePrimsFactory.getFactories());
    allFactories.addAll(IntegerPrimsFactory.getFactories());
    allFactories.addAll(MethodPrimsFactory.getFactories());
    allFactories.addAll(ObjectPrimsFactory.getFactories());
    allFactories.addAll(StringPrimsFactory.getFactories());
    allFactories.addAll(SystemPrimsFactory.getFactories());

    return allFactories;
  }

  private static HashMap<SSymbol, Dispatchable> constructVmMirrorPrimitives() {
    HashMap<SSymbol, Dispatchable> primitives = new HashMap<>();

    List<NodeFactory<? extends ExpressionNode>> primFacts = getFactories();
    for (NodeFactory<? extends ExpressionNode> primFact : primFacts) {
      som.primitives.Primitive prim = getPrimitiveAnnotation(primFact);
      if (prim != null) {
        primitives.put(Symbols.symbolFor(prim.value()),
            constructVmMirrorPrimitive(prim, primFact));
      }
    }

    return primitives;
  }

  public static som.primitives.Primitive getPrimitiveAnnotation(
      final NodeFactory<? extends ExpressionNode> primFact) {
    GeneratedBy[] genAnnotation = primFact.getClass().getAnnotationsByType(
        GeneratedBy.class);

    assert genAnnotation.length == 1; // should always be exactly one

    Class<?> nodeClass = genAnnotation[0].value();
    som.primitives.Primitive[] ann = nodeClass.getAnnotationsByType(
        som.primitives.Primitive.class);
    som.primitives.Primitive prim;
    if (ann.length == 1) {
      prim = ann[0];
    } else {
      prim = null;
      assert ann.length == 0;
    }
    return prim;
  }

  private static SObject constructVmMirror() {
    HashMap<SSymbol, Dispatchable> vmMirrorMethods = constructVmMirrorPrimitives();
    ClassScope scope = new ClassScope(null);

    ClassDefinition vmMirrorDef = new ClassDefinition(
        Symbols.symbolFor("VmMirror"), null, vmMirrorMethods, null, null,
        0, new ClassDefinitionId(), AccessModifier.PUBLIC, scope, scope, null);
    scope.setClassDefinition(vmMirrorDef, false);

    SClass vmMirrorClass = new SClass(null, null);
    vmMirrorDef.initializeClass(vmMirrorClass, null);

    return new SObject(vmMirrorClass);
  }

  /**
   * Allocate the metaclass class.
   */
  public static SClass newMetaclassClass(final SObject kernel) {
    SClass metaclassClass      = new SClass(kernel); // class obj for "Metaclass"
    SClass metaclassClassClass = new SClass(kernel); // class obj for "Metaclass class"
    metaclassClass.setClass(metaclassClassClass);

    metaclassClass.setName(Symbols.symbolFor("Metaclass"));
    metaclassClassClass.setName(Symbols.symbolFor("Metaclass class"));

    // Connect the metaclass hierarchy
    metaclassClass.getSOMClass().setClass(metaclassClass);
    return metaclassClass;
  }

  public static SClass newEmptyClassWithItsClass(final String name) {
    SClass clazz      = new SClass(KernelObj.kernel);
    SClass clazzClazz = new SClass(KernelObj.kernel);

    initializeClassAndItsClass(name, clazz, clazzClazz);

    // Return the freshly allocated system class
    return clazz;
  }

  public static void initializeClassAndItsClass(final String name,
      final SClass clazz, final SClass clazzClazz) {
    clazz.setName(Symbols.symbolFor(name));

    // Setup the metaclass hierarchy
    clazzClazz.setName(Symbols.symbolFor(name + " class"));
    clazz.setClass(clazzClazz);

    clazzClazz.setClass(metaclassClass);
    clazzClazz.setSuperClass(Classes.classClass);
  }

  public static void initializeObjectSystem() {
    assert platformModule != null && kernelModule != null;

    // these classes need to be defined by the Kernel module
    ClassDefinition topDef   = kernelModule.getEmbeddedClassDefinition("Top");
    ClassDefinition thingDef = kernelModule.getEmbeddedClassDefinition("Thing");
    thingDef.addSyntheticInitializerWithoutSuperSendOnlyForThingClass();
    ClassDefinition valueDef = kernelModule.getEmbeddedClassDefinition("Value");
    ClassDefinition nilDef   = kernelModule.getEmbeddedClassDefinition("Nil");

    ClassDefinition objectDef    = kernelModule.getEmbeddedClassDefinition("Object");
    ClassDefinition classDef     = kernelModule.getEmbeddedClassDefinition("Class");
    ClassDefinition metaclassDef = kernelModule.getEmbeddedClassDefinition("Metaclass");

    ClassDefinition arrayDef   = kernelModule.getEmbeddedClassDefinition("Array");
    ClassDefinition symbolDef  = kernelModule.getEmbeddedClassDefinition("Symbol");
    ClassDefinition integerDef = kernelModule.getEmbeddedClassDefinition("Integer");
    ClassDefinition stringDef  = kernelModule.getEmbeddedClassDefinition("String");
    ClassDefinition doubleDef  = kernelModule.getEmbeddedClassDefinition("Double");
    ClassDefinition booleanDef = kernelModule.getEmbeddedClassDefinition("Boolean");

    ClassDefinition blockDef  = kernelModule.getEmbeddedClassDefinition("Block");
    ClassDefinition block1Def = kernelModule.getEmbeddedClassDefinition("Block1");
    ClassDefinition block2Def = kernelModule.getEmbeddedClassDefinition("Block2");
    ClassDefinition block3Def = kernelModule.getEmbeddedClassDefinition("Block3");

    // some basic assumptions about
    assert    topDef.getNumberOfSlots() == 0;
    assert  thingDef.getNumberOfSlots() == 0;
    assert objectDef.getNumberOfSlots() == 0;
    assert  valueDef.getNumberOfSlots() == 0;

    assert KernelObj.kernel.getNumberOfFields() == kernelModule.getNumberOfSlots();

       topDef.initializeClass(Classes.topClass, null);  // Top doesn't have a super class
     thingDef.initializeClass(Classes.thingClass,  Classes.topClass);
     valueDef.initializeClass(Classes.valueClass,  Classes.thingClass);
    objectDef.initializeClass(Classes.objectClass, Classes.thingClass);
     classDef.initializeClass(Classes.classClass,  Classes.objectClass);
 metaclassDef.initializeClass(Classes.metaclassClass, Classes.classClass);
       nilDef.initializeClass(Classes.nilClass,    Classes.valueClass);

     arrayDef.initializeClass(Classes.arrayClass,   Classes.objectClass);
   integerDef.initializeClass(Classes.integerClass, Classes.valueClass);
    stringDef.initializeClass(Classes.stringClass,  Classes.valueClass);
    doubleDef.initializeClass(Classes.doubleClass,  Classes.valueClass);
   booleanDef.initializeClass(Classes.booleanClass, Classes.valueClass);
    symbolDef.initializeClass(Classes.symbolClass,  Classes.stringClass);

     blockDef.initializeClass(Classes.blockClass,  Classes.objectClass);
    block1Def.initializeClass(Classes.blockClass1, Classes.blockClass);
    block2Def.initializeClass(Classes.blockClass2, Classes.blockClass1);
    block3Def.initializeClass(Classes.blockClass3, Classes.blockClass2);

    Nil.nilObject.setClass(Classes.nilClass);

    SClass kernelClass = kernelModule.instantiateClass(Nil.nilObject, Classes.objectClass);
    KernelObj.kernel.setClass(kernelClass);

    // initialize slots of kernel object
    // TODO: try to actually use the initializer expressions...
    setSlot(KernelObj.kernel, "vmMirror",   constructVmMirror(), kernelModule);
    setSlot(KernelObj.kernel, "ObjectSlot", Classes.objectClass, kernelModule);
    setSlot(KernelObj.kernel, "ValueSlot",  Classes.valueClass,  kernelModule);

    platformClass = platformModule.instantiateClass();
  }

  private static void setSlot(final SObject obj, final String slotName,
      final Object value, final ClassDefinition classDef) {
    SlotDefinition slot = (SlotDefinition) classDef.getInstanceDispatchables().get(
        Symbols.symbolFor(slotName));
    slot.setValueDuringBootstrap(obj, value);
  }

  public static long executeApplication(final String appFile, final String[] args) {
    throw new NotYetImplementedException();
  }

  public static Object execute(final String selector) {
    Dispatchable method = platformClass.getSOMClass().lookupMessage(
        Symbols.symbolFor(selector), AccessModifier.PUBLIC);
    return method.invoke(platformClass);
  }
}
