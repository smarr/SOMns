package som.vm;

import static som.vm.constants.Classes.metaclassClass;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import som.VM;
import som.compiler.AccessModifier;
import som.compiler.MethodBuilder;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.compiler.MixinDefinition;
import som.compiler.MixinDefinition.SlotDefinition;
import som.compiler.SourcecodeCompiler;
import som.interpreter.LexicalScope.MixinScope;
import som.interpreter.Primitive;
import som.interpreter.actors.Actor;
import som.interpreter.actors.ResolvePromiseNodeFactory;
import som.interpreter.nodes.ArgumentReadNode.LocalArgumentReadNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.nodes.specialized.AndMessageNodeFactory;
import som.interpreter.nodes.specialized.NotMessageNodeFactory;
import som.interpreter.nodes.specialized.whileloops.WhilePrimitiveNodeFactory;
import som.primitives.AsStringPrimFactory;
import som.primitives.BlockPrimsFactory;
import som.primitives.ClassPrimsFactory;
import som.primitives.DoublePrimsFactory;
import som.primitives.EqualsEqualsPrimFactory;
import som.primitives.EqualsPrimFactory;
import som.primitives.ExceptionsPrimsFactory;
import som.primitives.HashPrimFactory;
import som.primitives.IntegerPrimsFactory;
import som.primitives.MethodPrimsFactory;
import som.primitives.MethodPrimsFactory.InvokeOnPrimFactory;
import som.primitives.MirrorPrimsFactory;
import som.primitives.ObjectPrimsFactory;
import som.primitives.ObjectPrimsFactory.IsValueFactory;
import som.primitives.ObjectSystemPrimsFactory;
import som.primitives.SizeAndLengthPrimFactory;
import som.primitives.StringPrimsFactory;
import som.primitives.SystemPrimsFactory;
import som.primitives.UnequalsPrimFactory;
import som.primitives.actors.ActorClassesFactory;
import som.primitives.actors.CreateActorPrimFactory;
import som.primitives.actors.PromisePrimsFactory;
import som.primitives.arithmetic.AdditionPrimFactory;
import som.primitives.arithmetic.DividePrimFactory;
import som.primitives.arithmetic.DoubleDivPrimFactory;
import som.primitives.arithmetic.ExpPrimFactory;
import som.primitives.arithmetic.LessThanPrimFactory;
import som.primitives.arithmetic.LogPrimFactory;
import som.primitives.arithmetic.ModuloPrimFactory;
import som.primitives.arithmetic.MultiplicationPrimFactory;
import som.primitives.arithmetic.RemainderPrimFactory;
import som.primitives.arithmetic.SinPrimFactory;
import som.primitives.arithmetic.SqrtPrimFactory;
import som.primitives.arithmetic.SubtractionPrimFactory;
import som.primitives.arrays.AtPrimFactory;
import som.primitives.arrays.AtPutPrimFactory;
import som.primitives.arrays.DoIndexesPrimFactory;
import som.primitives.arrays.NewPrimFactory;
import som.primitives.arrays.PutAllNodeFactory;
import som.primitives.arrays.ToArgumentsArrayNodeGen;
import som.primitives.bitops.BitAndPrimFactory;
import som.primitives.bitops.BitXorPrimFactory;
import som.vm.constants.Classes;
import som.vm.constants.KernelObj;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.dsl.NodeFactory;


public final class ObjectSystem {

  @CompilationFinal
  private static ObjectSystem last;

  private final Map<String, MixinDefinition> loadedModules;

  private final MixinDefinition platformModule;
  private final MixinDefinition kernelModule;

  @CompilationFinal
  private SClass platformClass;  // is only set after completion of initialize()

  @CompilationFinal
  private boolean initialized = false;

  public ObjectSystem(final String platformFilename,
      final String kernelFilename) throws IOException {
    last = this;
    loadedModules  = new LinkedHashMap<>();
    platformModule = loadModule(platformFilename);
    kernelModule   = loadModule(kernelFilename);
  }

  public static boolean isInitialized() {
    return last.initialized;
  }

  public MixinDefinition loadModule(final String filename)
      throws IOException {
    File file = new File(filename);

    if (loadedModules.containsKey(file.getAbsolutePath())) {
      return loadedModules.get(file.getAbsolutePath());
    }

    MixinDefinition module = SourcecodeCompiler.compileModule(file);
    loadedModules.put(file.getAbsolutePath(), module);

    return module;
  }

  private static SInvokable constructVmMirrorPrimitive(
      final SSymbol signature,
      final som.primitives.Primitive primitive,
      final NodeFactory<? extends ExpressionNode> factory) {
    CompilerAsserts.neverPartOfCompilation("This is only executed during bootstrapping.");
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
              SizeAndLengthPrimFactory.create(null));
//        } else if (factory == SpawnWithArgsPrimFactory.getInstance()) {
//          primNode = factory.createNode(args[0], args[1],
//              ToArgumentsArrayNodeGen.create(null, null));
        } else if (factory == CreateActorPrimFactory.getInstance()) {
          primNode = factory.createNode(args[0], args[1],
              IsValueFactory.create(null));
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
    return new SInvokable(signature, AccessModifier.PUBLIC, null,
        primMethodNode, null);
  }

  private static List<NodeFactory<? extends ExpressionNode>> getFactories() {
    List<NodeFactory<? extends ExpressionNode>> allFactories = new ArrayList<>();
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
    allFactories.addAll(ObjectSystemPrimsFactory.getFactories());
    allFactories.addAll(MirrorPrimsFactory.getFactories());
    allFactories.addAll(ExceptionsPrimsFactory.getFactories());
    allFactories.addAll(ActorClassesFactory.getFactories());
    allFactories.addAll(PromisePrimsFactory.getFactories());

    allFactories.add(EqualsEqualsPrimFactory.getInstance());
    allFactories.add(EqualsPrimFactory.getInstance());
    allFactories.add(NotMessageNodeFactory.getInstance());
    allFactories.add(AsStringPrimFactory.getInstance());
    allFactories.add(HashPrimFactory.getInstance());
    allFactories.add(SizeAndLengthPrimFactory.getInstance());
    allFactories.add(UnequalsPrimFactory.getInstance());
    allFactories.add(AdditionPrimFactory.getInstance());
    allFactories.add(BitXorPrimFactory.getInstance());
    allFactories.add(BitAndPrimFactory.getInstance());
    allFactories.add(DividePrimFactory.getInstance());
    allFactories.add(DoubleDivPrimFactory.getInstance());
    allFactories.add(LessThanPrimFactory.getInstance());
    allFactories.add(ModuloPrimFactory.getInstance());
    allFactories.add(MultiplicationPrimFactory.getInstance());
    allFactories.add(RemainderPrimFactory.getInstance());
    allFactories.add(ExpPrimFactory.getInstance());
    allFactories.add(LogPrimFactory.getInstance());
    allFactories.add(SinPrimFactory.getInstance());
    allFactories.add(SqrtPrimFactory.getInstance());
    allFactories.add(SubtractionPrimFactory.getInstance());
    allFactories.add(AtPrimFactory.getInstance());
    allFactories.add(AtPutPrimFactory.getInstance());
    allFactories.add(DoIndexesPrimFactory.getInstance());
    allFactories.add(NewPrimFactory.getInstance());
    allFactories.add(PutAllNodeFactory.getInstance());

    allFactories.add(CreateActorPrimFactory.getInstance());
    allFactories.add(ResolvePromiseNodeFactory.getInstance());

    return allFactories;
  }

  private static HashMap<SSymbol, Dispatchable> constructVmMirrorPrimitives() {
    HashMap<SSymbol, Dispatchable> primitives = new HashMap<>();

    List<NodeFactory<? extends ExpressionNode>> primFacts = getFactories();
    for (NodeFactory<? extends ExpressionNode> primFact : primFacts) {
      som.primitives.Primitive prim = getPrimitiveAnnotation(primFact);
      if (prim != null) {
        for (String sig : prim.value()) {
          SSymbol signature = Symbols.symbolFor(sig);
          primitives.put(signature,
              constructVmMirrorPrimitive(signature, prim, primFact));
        }
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

  private static SObjectWithoutFields constructVmMirror() {
    HashMap<SSymbol, Dispatchable> vmMirrorMethods = constructVmMirrorPrimitives();
    MixinScope scope = new MixinScope(null);

    MixinDefinition vmMirrorDef = new MixinDefinition(
        Symbols.VMMIRROR, null, null, null, null, null, null,
        vmMirrorMethods, null,
        null, new MixinDefinitionId(Symbols.VMMIRROR), AccessModifier.PUBLIC, scope, scope,
        true, true, true, null);
    scope.setMixinDefinition(vmMirrorDef, false);

    SClass vmMirrorClass = vmMirrorDef.instantiateClass(Nil.nilObject, new SClass[] {Classes.topClass, Classes.valueClass});
    return new SObjectWithoutFields(vmMirrorClass, vmMirrorClass.getInstanceFactory());
  }

  /**
   * Allocate the metaclass class.
   */
  public static SClass newMetaclassClass(final SObject kernel) {
    SClass metaclassClass      = new SClass(kernel);
    SClass metaclassClassClass = new SClass(kernel);
    metaclassClass.setClass(metaclassClassClass);

    metaclassClass.initializeClass(Symbols.METACLASS, null);
    metaclassClassClass.initializeClass(Symbols.METACLASS_CLASS, null);

    // Connect the metaclass hierarchy
    metaclassClass.getSOMClass().setClass(metaclassClass);
    return metaclassClass;
  }

  public static SClass newEmptyClassWithItsClass(final String name) {
    SClass clazz      = new SClass(KernelObj.kernel);
    SClass clazzClazz = new SClass(KernelObj.kernel);

    initializeClassAndItsClass(name, clazz, clazzClazz);
    return clazz;
  }

  public static void initializeClassAndItsClass(final String name,
      final SClass clazz, final SClass clazzClazz) {
    clazz.initializeClass(Symbols.symbolFor(name), null);

    // Setup the metaclass hierarchy
    clazzClazz.initializeClass(Symbols.symbolFor(name + " class"), Classes.classClass);

    clazz.setClass(clazzClazz);
    clazzClazz.setClass(metaclassClass);
  }

  public SObjectWithoutFields initialize() {
    assert platformModule != null && kernelModule != null;

    // these classes need to be defined by the Kernel module
    MixinDefinition topDef   = kernelModule.getNestedMixinDefinition("Top");
    MixinDefinition thingDef = kernelModule.getNestedMixinDefinition("Thing");
    thingDef.addSyntheticInitializerWithoutSuperSendOnlyForThingClass();
    MixinDefinition valueDef = kernelModule.getNestedMixinDefinition("Value");
    MixinDefinition transferDef = kernelModule.getNestedMixinDefinition("TransferObject");
    MixinDefinition nilDef   = kernelModule.getNestedMixinDefinition("Nil");

    MixinDefinition objectDef    = kernelModule.getNestedMixinDefinition("Object");
    MixinDefinition classDef     = kernelModule.getNestedMixinDefinition("Class");
    MixinDefinition metaclassDef = kernelModule.getNestedMixinDefinition("Metaclass");

    MixinDefinition arrayReadMixinDef = kernelModule.getNestedMixinDefinition("ArrayReadMixin");
    MixinDefinition arrayDef   = kernelModule.getNestedMixinDefinition("Array");
    MixinDefinition valueArrayDef = kernelModule.getNestedMixinDefinition("ValueArray");
    MixinDefinition transferArrayDef = kernelModule.getNestedMixinDefinition("TransferArray");
    MixinDefinition symbolDef  = kernelModule.getNestedMixinDefinition("Symbol");
    MixinDefinition integerDef = kernelModule.getNestedMixinDefinition("Integer");
    MixinDefinition stringDef  = kernelModule.getNestedMixinDefinition("String");
    MixinDefinition doubleDef  = kernelModule.getNestedMixinDefinition("Double");

    MixinDefinition booleanDef = kernelModule.getNestedMixinDefinition("Boolean");
    MixinDefinition trueDef    = kernelModule.getNestedMixinDefinition("True");
    MixinDefinition falseDef   = kernelModule.getNestedMixinDefinition("False");

    MixinDefinition blockDef  = kernelModule.getNestedMixinDefinition("Block");
    MixinDefinition block1Def = kernelModule.getNestedMixinDefinition("Block1");
    MixinDefinition block2Def = kernelModule.getNestedMixinDefinition("Block2");
    MixinDefinition block3Def = kernelModule.getNestedMixinDefinition("Block3");

    // some basic assumptions about
    assert      topDef.getNumberOfSlots() == 0;
    assert    thingDef.getNumberOfSlots() == 0;
    assert   objectDef.getNumberOfSlots() == 0;
    assert    valueDef.getNumberOfSlots() == 0;
    assert transferDef.getNumberOfSlots() == 0;

       topDef.initializeClass(Classes.topClass, null);  // Top doesn't have a super class
     thingDef.initializeClass(Classes.thingClass,  Classes.topClass);
     valueDef.initializeClass(Classes.valueClass,  Classes.thingClass, true, false, false);
    objectDef.initializeClass(Classes.objectClass, Classes.thingClass);
     classDef.initializeClass(Classes.classClass,  Classes.objectClass);
  transferDef.initializeClass(Classes.transferClass, Classes.objectClass, false, true, false);

 metaclassDef.initializeClass(Classes.metaclassClass, Classes.classClass);
       nilDef.initializeClass(Classes.nilClass,    Classes.valueClass);

arrayReadMixinDef.initializeClass(Classes.arrayReadMixinClass, Classes.objectClass);
     arrayDef.initializeClass(Classes.arrayClass,   new SClass[] {Classes.objectClass, Classes.arrayReadMixinClass}, false, false, true);
valueArrayDef.initializeClass(Classes.valueArrayClass, new SClass[] {Classes.valueClass, Classes.arrayReadMixinClass}, false, false, true);
transferArrayDef.initializeClass(Classes.transferArrayClass, new SClass[] {Classes.arrayClass, Classes.transferClass}, false, false, true);
   integerDef.initializeClass(Classes.integerClass, Classes.valueClass);
    stringDef.initializeClass(Classes.stringClass,  Classes.valueClass);
    doubleDef.initializeClass(Classes.doubleClass,  Classes.valueClass);
    symbolDef.initializeClass(Classes.symbolClass,  Classes.stringClass);

   booleanDef.initializeClass(Classes.booleanClass, Classes.valueClass);
      trueDef.initializeClass(Classes.trueClass,    Classes.booleanClass);
     falseDef.initializeClass(Classes.falseClass,   Classes.booleanClass);

     blockDef.initializeClass(Classes.blockClass,  Classes.objectClass);
    block1Def.initializeClass(Classes.blockClass1, Classes.blockClass);
    block2Def.initializeClass(Classes.blockClass2, Classes.blockClass1);
    block3Def.initializeClass(Classes.blockClass3, Classes.blockClass2);

    Nil.nilObject.setClass(Classes.nilClass);

    // fix up the metaclassClass group
    Classes.topClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
  Classes.thingClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
  Classes.valueClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
 Classes.objectClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
Classes.transferClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
  Classes.classClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
  Classes.metaclassClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
  Classes.nilClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
  Classes.arrayReadMixinClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
  Classes.arrayClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
  Classes.valueArrayClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
  Classes.transferArrayClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
  Classes.integerClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
  Classes.stringClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
  Classes.doubleClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
  Classes.symbolClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
  Classes.booleanClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
  Classes.trueClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
  Classes.falseClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
  Classes.blockClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
  Classes.blockClass1.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
  Classes.blockClass2.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
  Classes.blockClass3.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());


    SClass kernelClass = kernelModule.instantiateClass(Nil.nilObject, Classes.objectClass);
    KernelObj.kernel.setClass(kernelClass);

    // create and initialize the vmMirror object
    SObjectWithoutFields vmMirror = constructVmMirror();
    assert vmMirror.isValue();

    // initialize slots of kernel object
    setSlot(KernelObj.kernel, "vmMirror",   vmMirror, kernelModule);
    setSlot(KernelObj.kernel, "ObjectSlot", Classes.objectClass, kernelModule);
    setSlot(KernelObj.kernel, "ValueSlot",  Classes.valueClass,  kernelModule);

    // Initialize the class cache slots
    setSlot(KernelObj.kernel, "Top",       Classes.topClass,       kernelModule);
    setSlot(KernelObj.kernel, "Thing",     Classes.thingClass,     kernelModule);
    setSlot(KernelObj.kernel, "Object",    Classes.objectClass,    kernelModule);
    setSlot(KernelObj.kernel, "Value",     Classes.valueClass,     kernelModule);
    setSlot(KernelObj.kernel, "TransferObject", Classes.transferClass, kernelModule);
    setSlot(KernelObj.kernel, "Class",     Classes.classClass,     kernelModule);
    setSlot(KernelObj.kernel, "Metaclass", Classes.metaclassClass, kernelModule);
    setSlot(KernelObj.kernel, "Boolean",   Classes.booleanClass,   kernelModule);
    setSlot(KernelObj.kernel, "True",      Classes.trueClass,      kernelModule);
    setSlot(KernelObj.kernel, "False",     Classes.falseClass,     kernelModule);
    setSlot(KernelObj.kernel, "Nil",       Classes.nilClass,       kernelModule);
    setSlot(KernelObj.kernel, "Integer",   Classes.integerClass,   kernelModule);
    setSlot(KernelObj.kernel, "Double",    Classes.doubleClass,    kernelModule);
    setSlot(KernelObj.kernel, "Class",     Classes.classClass,     kernelModule);
    setSlot(KernelObj.kernel, "String",    Classes.stringClass,    kernelModule);
    setSlot(KernelObj.kernel, "Symbol",    Classes.symbolClass,    kernelModule);
    setSlot(KernelObj.kernel, "ArrayReadMixin", Classes.arrayReadMixinClass, kernelModule);
    setSlot(KernelObj.kernel, "Array",     Classes.arrayClass,     kernelModule);
    setSlot(KernelObj.kernel, "ValueArray", Classes.valueArrayClass, kernelModule);
    setSlot(KernelObj.kernel, "TransferArray", Classes.transferArrayClass, kernelModule);
    setSlot(KernelObj.kernel, "Block",     Classes.blockClass,     kernelModule);
    setSlot(KernelObj.kernel, "Block1",    Classes.blockClass1,    kernelModule);
    setSlot(KernelObj.kernel, "Block2",    Classes.blockClass2,    kernelModule);
    setSlot(KernelObj.kernel, "Block3",    Classes.blockClass3,    kernelModule);

    initialized = true;

    platformClass = platformModule.instantiateModuleClass();
    return vmMirror;
  }

  private static void setSlot(final SObject obj, final String slotName,
      final Object value, final MixinDefinition classDef) {
    SlotDefinition slot = (SlotDefinition) classDef.getInstanceDispatchables().get(
        Symbols.symbolFor(slotName));
    slot.setValueDuringBootstrap(obj, value);
  }

  public void executeApplication(final SObjectWithoutFields vmMirror, final Actor mainActor) {
    Object platform = platformModule.instantiateObject(platformClass, vmMirror);
    SInvokable disp = (SInvokable) platformClass.lookupMessage(
        Symbols.symbolFor("start"), AccessModifier.PUBLIC);
    Object returnCode = disp.invoke(platform);

    if (VM.isUsingActors()) {
      mainActor.relinuqishMainThreadAndMoveExecutionToPool();
      VM.setMainThread(Thread.currentThread());

      int emptyFJPool = 0;
      while (emptyFJPool < 30 && !VM.shouldExit()) {
        try { Thread.sleep(1000); } catch (InterruptedException e) { }
        if (Actor.isPoolIdle()) {
          emptyFJPool++;
        }
      }

      if (!VM.isAvoidingExit() || !VM.shouldExit()) {
        VM.errorExit("This should never happen. The VM should not return under those conditions.");
        System.exit(1); // just in case it was disable for VM.errorExit
      }
    } else if (!VM.isAvoidingExit()) {
      System.exit((int) (long) returnCode);
    }
  }

  public Object execute(final String selector) {
    SInvokable method = (SInvokable) platformClass.getSOMClass().lookupMessage(
        Symbols.symbolFor(selector), AccessModifier.PUBLIC);
    return method.invoke(platformClass);
  }
}
