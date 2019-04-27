package som.vm;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import bd.basic.ProgramDefinitionError;
import bd.inlining.InlinableNodes;
import bd.tools.structure.StructuralProbe;
import som.Launcher;
import som.Output;
import som.VM;
import som.compiler.AccessModifier;
import som.compiler.MethodBuilder;
import som.compiler.MixinBuilder;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.compiler.MixinDefinition;
import som.compiler.MixinDefinition.SlotDefinition;
import som.compiler.SourcecodeCompiler;
import som.compiler.Variable;
import som.interpreter.LexicalScope.MixinScope;
import som.interpreter.SomLanguage;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage.DirectMessage;
import som.interpreter.actors.EventualSendNode;
import som.interpreter.actors.SPromise;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.objectstorage.ClassFactory;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.vm.constants.Classes;
import som.vm.constants.KernelObj;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import som.vmobjects.SSymbol;
import tools.concurrency.TracingActors;
import tools.snapshot.nodes.AbstractArraySerializationNodeGen.ArraySerializationNodeFactory;
import tools.snapshot.nodes.AbstractArraySerializationNodeGen.TransferArraySerializationNodeFactory;
import tools.snapshot.nodes.AbstractArraySerializationNodeGen.ValueArraySerializationNodeFactory;
import tools.snapshot.nodes.AbstractSerializationNode;
import tools.snapshot.nodes.BlockSerializationNodeFactory;
import tools.snapshot.nodes.MessageSerializationNodeFactory;
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
import tools.snapshot.nodes.SerializerRootNode;


public final class ObjectSystem {

  static {
    inlinableNodes = new InlinableNodes<>(Symbols.PROVIDER,
        Primitives.getInlinableNodes(), Primitives.getInlinableFactories());
  }

  private final EconomicMap<URI, MixinDefinition> loadedModules;

  @CompilationFinal private MixinDefinition platformModule;
  @CompilationFinal private MixinDefinition kernelModule;

  @CompilationFinal private SClass platformClass; // is only set after completion of
                                                  // initialize()

  @CompilationFinal private boolean initialized = false;

  private final SourcecodeCompiler compiler;

  private final StructuralProbe<SSymbol, MixinDefinition, SInvokable, SlotDefinition, Variable> structuralProbe;

  private final Primitives primitives;

  private static final InlinableNodes<SSymbol> inlinableNodes;

  private CompletableFuture<Object> mainThreadCompleted;

  private final VM vm;

  public ObjectSystem(final SourcecodeCompiler compiler,
      final StructuralProbe<SSymbol, MixinDefinition, SInvokable, SlotDefinition, Variable> probe,
      final VM vm) {
    this.primitives = new Primitives(compiler.getLanguage());
    this.compiler = compiler;
    structuralProbe = probe;
    loadedModules = EconomicMap.create();
    this.vm = vm;
  }

  public void loadKernelAndPlatform(final String platformFilename,
      final String kernelFilename) throws IOException {
    platformModule = loadModule(platformFilename);
    kernelModule = loadModule(kernelFilename);
  }

  public boolean isInitialized() {
    return initialized;
  }

  public Primitives getPrimitives() {
    return primitives;
  }

  public InlinableNodes<SSymbol> getInlinableNodes() {
    return inlinableNodes;
  }

  public SClass getPlatformClass() {
    assert platformClass != null;
    return platformClass;
  }

  public SClass loadExtensionModule(final String filename) {
    ExtensionLoader loader = new ExtensionLoader(filename, compiler.getLanguage());
    EconomicMap<SSymbol, Dispatchable> primitives = loader.getPrimitives();
    MixinDefinition mixin = constructPrimitiveMixin(filename, primitives);
    return mixin.instantiateClass(Nil.nilObject, Classes.topClass);
  }

  public MixinDefinition loadModule(final String filename) throws IOException {
    File file = new File(filename);

    if (!file.exists()) {
      throw new FileNotFoundException(filename);
    }

    if (!file.isFile()) {
      throw new NotAFileException(filename);
    }

    Source source = SomLanguage.getSource(file);
    return loadModule(source);
  }

  public MixinDefinition loadModule(final Source source) throws IOException {
    URI uri = source.getURI();
    if (loadedModules.containsKey(uri)) {
      return loadedModules.get(uri);
    }

    MixinDefinition module;
    try {
      module = compiler.compileModule(source, structuralProbe);
      loadedModules.put(uri, module);
      return module;
    } catch (ProgramDefinitionError e) {
      vm.errorExit(e.toString());
      throw new IOException(e);
    }
  }

  private SObjectWithoutFields constructVmMirror() {
    EconomicMap<SSymbol, Dispatchable> vmMirrorMethods = primitives.takeVmMirrorPrimitives();
    SClass vmMirrorClass = constructPrimitiveClass(vmMirrorMethods);
    return new SObjectWithoutFields(vmMirrorClass, vmMirrorClass.getInstanceFactory());
  }

  private MixinDefinition constructPrimitiveMixin(final String module,
      final EconomicMap<SSymbol, Dispatchable> primitives) {
    SSymbol moduleName = Symbols.symbolFor(module);
    Source source = SomLanguage.getSyntheticSource("", module + "-extension-primitives");
    SourceSection ss = source.createSection(1);

    MixinBuilder mixin = new MixinBuilder(null, AccessModifier.PUBLIC, moduleName,
        ss, null, compiler.getLanguage());
    MethodBuilder primFactor = mixin.getPrimaryFactoryMethodBuilder();

    primFactor.addArgument(Symbols.SELF, ss);
    primFactor.setSignature(Symbols.NEW);

    mixin.setupInitializerBasedOnPrimaryFactory(ss);
    mixin.setInitializerSource(ss);
    mixin.finalizeInitializer();
    mixin.setSuperClassResolution(mixin.constructSuperClassResolution(Symbols.TOP, ss));

    // we do not need any initialization, and super is top, so, simply return self
    mixin.setSuperclassFactorySend(mixin.getInitializerMethodBuilder().getSelfRead(ss), false);

    mixin.addMethods(primitives);

    return mixin.assemble(ss);
  }

  private SClass constructPrimitiveClass(final EconomicMap<SSymbol, Dispatchable> primitives) {
    MixinScope scope = new MixinScope(null);

    MixinDefinition vmMirrorDef = new MixinDefinition(
        Symbols.VMMIRROR, null, null, null, null, null, null, null,
        primitives, null,
        null, new MixinDefinitionId(Symbols.VMMIRROR), AccessModifier.PUBLIC, scope, scope,
        true, true, true, null);
    scope.setMixinDefinition(vmMirrorDef, false);

    SClass vmMirrorClass = vmMirrorDef.instantiateClass(Nil.nilObject,
        new SClass[] {Classes.topClass, Classes.valueClass});
    return vmMirrorClass;
  }

  /**
   * Allocate the metaclass class.
   */
  public static SClass newMetaclassClass(final SObject kernel) {
    SClass metaclassClass = new SClass(kernel);
    SClass metaclassClassClass = new SClass(kernel);
    metaclassClass.setClass(metaclassClassClass);

    metaclassClass.initializeClass(Symbols.METACLASS, null);
    metaclassClassClass.initializeClass(Symbols.METACLASS_CLASS, null);

    // Connect the metaclass hierarchy
    metaclassClass.getSOMClass().setClass(metaclassClass);
    return metaclassClass;
  }

  public static SClass newEmptyClassWithItsClass(final String name) {
    SClass clazz = new SClass(KernelObj.kernel);
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
    clazzClazz.setClass(Classes.metaclassClass);
  }

  public SObjectWithoutFields initialize() {
    ObjectTransitionSafepoint.INSTANCE.register();
    assert platformModule != null && kernelModule != null;

    // these classes need to be defined by the Kernel module
    MixinDefinition topDef = kernelModule.getNestedMixinDefinition("Top");
    MixinDefinition thingDef = kernelModule.getNestedMixinDefinition("Thing");
    thingDef.addSyntheticInitializerWithoutSuperSendOnlyForThingClass();
    MixinDefinition valueDef = kernelModule.getNestedMixinDefinition("Value");
    MixinDefinition transferDef = kernelModule.getNestedMixinDefinition("TransferObject");
    MixinDefinition nilDef = kernelModule.getNestedMixinDefinition("Nil");

    MixinDefinition objectDef = kernelModule.getNestedMixinDefinition("Object");
    MixinDefinition classDef = kernelModule.getNestedMixinDefinition("Class");
    MixinDefinition metaclassDef = kernelModule.getNestedMixinDefinition("Metaclass");

    MixinDefinition arrayReadMixinDef =
        kernelModule.getNestedMixinDefinition("ArrayReadMixin");
    MixinDefinition arrayDef = kernelModule.getNestedMixinDefinition("Array");
    MixinDefinition valueArrayDef = kernelModule.getNestedMixinDefinition("ValueArray");
    MixinDefinition transferArrayDef = kernelModule.getNestedMixinDefinition("TransferArray");
    MixinDefinition symbolDef = kernelModule.getNestedMixinDefinition("Symbol");
    MixinDefinition integerDef = kernelModule.getNestedMixinDefinition("Integer");
    MixinDefinition stringDef = kernelModule.getNestedMixinDefinition("String");
    MixinDefinition doubleDef = kernelModule.getNestedMixinDefinition("Double");

    MixinDefinition booleanDef = kernelModule.getNestedMixinDefinition("Boolean");
    MixinDefinition trueDef = kernelModule.getNestedMixinDefinition("True");
    MixinDefinition falseDef = kernelModule.getNestedMixinDefinition("False");

    MixinDefinition blockDef = kernelModule.getNestedMixinDefinition("Block");

    // some basic assumptions about
    assert topDef.getNumberOfSlots() == 0;
    assert thingDef.getNumberOfSlots() == 0;
    assert objectDef.getNumberOfSlots() == 0;
    assert valueDef.getNumberOfSlots() == 0;
    assert transferDef.getNumberOfSlots() == 0;

    if (VmSettings.SNAPSHOTS_ENABLED) {
      SerializerRootNode.initializeSerialization(compiler.getLanguage());

      topDef.initializeClass(Classes.topClass, null,
          SObjectWithoutFieldsSerializationNodeFactory.getInstance()); // Top doesn't have a
                                                                       // super class
      thingDef.initializeClass(Classes.thingClass, Classes.topClass,
          SObjectWithoutFieldsSerializationNodeFactory.getInstance());
      valueDef.initializeClass(Classes.valueClass, Classes.thingClass, true, false, false,
          SObjectWithoutFieldsSerializationNodeFactory.getInstance());
      objectDef.initializeClass(Classes.objectClass, Classes.thingClass,
          SObjectWithoutFieldsSerializationNodeFactory.getInstance());
      classDef.initializeClass(Classes.classClass, Classes.objectClass,
          ClassSerializationNodeFactory.getInstance());
      transferDef.initializeClass(Classes.transferClass, Classes.objectClass, false, true,
          false, SObjectWithoutFieldsSerializationNodeFactory.getInstance());

      metaclassDef.initializeClass(Classes.metaclassClass, Classes.classClass,
          ClassSerializationNodeFactory.getInstance());
      nilDef.initializeClass(Classes.nilClass, Classes.valueClass,
          NilSerializationNodeFactory.getInstance());

      arrayReadMixinDef.initializeClass(Classes.arrayReadMixinClass, Classes.objectClass,
          SObjectWithoutFieldsSerializationNodeFactory.getInstance());
      arrayDef.initializeClass(Classes.arrayClass,
          new SClass[] {Classes.objectClass, Classes.arrayReadMixinClass}, false, false, true,
          ArraySerializationNodeFactory.getInstance());
      valueArrayDef.initializeClass(Classes.valueArrayClass,
          new SClass[] {Classes.valueClass, Classes.arrayReadMixinClass}, false, false, true,
          ValueArraySerializationNodeFactory.getInstance());
      transferArrayDef.initializeClass(Classes.transferArrayClass,
          new SClass[] {Classes.arrayClass, Classes.transferClass}, false, false, true,
          TransferArraySerializationNodeFactory.getInstance());
      integerDef.initializeClass(Classes.integerClass, Classes.valueClass,
          IntegerSerializationNodeFactory.getInstance());
      stringDef.initializeClass(Classes.stringClass, Classes.valueClass,
          StringSerializationNodeFactory.getInstance());
      doubleDef.initializeClass(Classes.doubleClass, Classes.valueClass,
          DoubleSerializationNodeFactory.getInstance());
      symbolDef.initializeClass(Classes.symbolClass, Classes.stringClass,
          SymbolSerializationNodeFactory.getInstance());

      booleanDef.initializeClass(Classes.booleanClass, Classes.valueClass,
          BooleanSerializationNodeFactory.getInstance());
      trueDef.initializeClass(Classes.trueClass, Classes.booleanClass,
          TrueSerializationNodeFactory.getInstance());
      falseDef.initializeClass(Classes.falseClass, Classes.booleanClass,
          FalseSerializationNodeFactory.getInstance());

      blockDef.initializeClass(Classes.blockClass, Classes.objectClass,
          BlockSerializationNodeFactory.getInstance());
    } else {
      topDef.initializeClass(Classes.topClass, null); // Top doesn't have a
                                                      // super class
      thingDef.initializeClass(Classes.thingClass, Classes.topClass);
      valueDef.initializeClass(Classes.valueClass, Classes.thingClass, true, false, false);
      objectDef.initializeClass(Classes.objectClass, Classes.thingClass);
      classDef.initializeClass(Classes.classClass, Classes.objectClass);
      transferDef.initializeClass(Classes.transferClass, Classes.objectClass, false, true,
          false);

      metaclassDef.initializeClass(Classes.metaclassClass, Classes.classClass);
      nilDef.initializeClass(Classes.nilClass, Classes.valueClass);

      arrayReadMixinDef.initializeClass(Classes.arrayReadMixinClass, Classes.objectClass);
      arrayDef.initializeClass(Classes.arrayClass,
          new SClass[] {Classes.objectClass, Classes.arrayReadMixinClass}, false, false, true);
      valueArrayDef.initializeClass(Classes.valueArrayClass,
          new SClass[] {Classes.valueClass, Classes.arrayReadMixinClass}, false, false, true);
      transferArrayDef.initializeClass(Classes.transferArrayClass,
          new SClass[] {Classes.arrayClass, Classes.transferClass}, false, false, true);
      integerDef.initializeClass(Classes.integerClass, Classes.valueClass);
      stringDef.initializeClass(Classes.stringClass, Classes.valueClass);
      doubleDef.initializeClass(Classes.doubleClass, Classes.valueClass);
      symbolDef.initializeClass(Classes.symbolClass, Classes.stringClass);

      booleanDef.initializeClass(Classes.booleanClass, Classes.valueClass);
      trueDef.initializeClass(Classes.trueClass, Classes.booleanClass);
      falseDef.initializeClass(Classes.falseClass, Classes.booleanClass);

      blockDef.initializeClass(Classes.blockClass, Classes.objectClass);
    }

    Nil.nilObject.setClass(Classes.nilClass);

    // fix up the metaclassClass group
    Classes.topClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.thingClass.getSOMClass()
                      .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.valueClass.getSOMClass()
                      .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.objectClass.getSOMClass()
                       .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.transferClass.getSOMClass()
                         .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.classClass.getSOMClass()
                      .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.metaclassClass.getSOMClass()
                          .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.nilClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.arrayReadMixinClass.getSOMClass()
                               .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.arrayClass.getSOMClass()
                      .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.valueArrayClass.getSOMClass()
                           .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.transferArrayClass.getSOMClass()
                              .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.integerClass.getSOMClass()
                        .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.stringClass.getSOMClass()
                       .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.doubleClass.getSOMClass()
                       .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.symbolClass.getSOMClass()
                       .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.booleanClass.getSOMClass()
                        .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.trueClass.getSOMClass().setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.falseClass.getSOMClass()
                      .setClassGroup(Classes.metaclassClass.getInstanceFactory());
    Classes.blockClass.getSOMClass()
                      .setClassGroup(Classes.metaclassClass.getInstanceFactory());

    // these classes are not exposed in Newspeak directly, and thus, do not yet have a class
    // factory
    setDummyClassFactory(Classes.messageClass, MessageSerializationNodeFactory.getInstance());
    setDummyClassFactory(Classes.methodClass,
        SInvokableSerializationNodeFactory.getInstance());

    SClass kernelClass = kernelModule.instantiateClass(Nil.nilObject, Classes.objectClass);
    KernelObj.kernel.setClass(kernelClass);

    // create and initialize the vmMirror object
    SObjectWithoutFields vmMirror = constructVmMirror();
    assert vmMirror.isValue();

    // initialize slots of kernel object
    setSlot(KernelObj.kernel, "vmMirror", vmMirror, kernelModule);
    setSlot(KernelObj.kernel, "ObjectSlot", Classes.objectClass, kernelModule);
    setSlot(KernelObj.kernel, "ValueSlot", Classes.valueClass, kernelModule);

    // Initialize the class cache slots
    setSlot(KernelObj.kernel, "Top", Classes.topClass, kernelModule);
    setSlot(KernelObj.kernel, "Thing", Classes.thingClass, kernelModule);
    setSlot(KernelObj.kernel, "Object", Classes.objectClass, kernelModule);
    setSlot(KernelObj.kernel, "Value", Classes.valueClass, kernelModule);
    setSlot(KernelObj.kernel, "TransferObject", Classes.transferClass, kernelModule);
    setSlot(KernelObj.kernel, "Class", Classes.classClass, kernelModule);
    setSlot(KernelObj.kernel, "Metaclass", Classes.metaclassClass, kernelModule);
    setSlot(KernelObj.kernel, "Boolean", Classes.booleanClass, kernelModule);
    setSlot(KernelObj.kernel, "True", Classes.trueClass, kernelModule);
    setSlot(KernelObj.kernel, "False", Classes.falseClass, kernelModule);
    setSlot(KernelObj.kernel, "Nil", Classes.nilClass, kernelModule);
    setSlot(KernelObj.kernel, "Integer", Classes.integerClass, kernelModule);
    setSlot(KernelObj.kernel, "Double", Classes.doubleClass, kernelModule);
    setSlot(KernelObj.kernel, "Class", Classes.classClass, kernelModule);
    setSlot(KernelObj.kernel, "String", Classes.stringClass, kernelModule);
    setSlot(KernelObj.kernel, "Symbol", Classes.symbolClass, kernelModule);
    setSlot(KernelObj.kernel, "ArrayReadMixin", Classes.arrayReadMixinClass, kernelModule);
    setSlot(KernelObj.kernel, "Array", Classes.arrayClass, kernelModule);
    setSlot(KernelObj.kernel, "ValueArray", Classes.valueArrayClass, kernelModule);
    setSlot(KernelObj.kernel, "TransferArray", Classes.transferArrayClass, kernelModule);
    setSlot(KernelObj.kernel, "Block", Classes.blockClass, kernelModule);

    initialized = true;

    platformClass = platformModule.instantiateModuleClass();

    ObjectTransitionSafepoint.INSTANCE.unregister();
    return vmMirror;
  }

  public void setDummyClassFactory(final SClass clazz,
      final NodeFactory<? extends AbstractSerializationNode> serializerFactory) {
    if (VmSettings.SNAPSHOTS_ENABLED) {
      ClassFactory classFactory = new ClassFactory(clazz.getSOMClass().getName(), null,
          null, null, true,
          true, false,
          null, false,
          null, serializerFactory);

      clazz.setClassGroup(classFactory);
      clazz.initializeStructure(null, null, null, true, false, false, classFactory);
    }
  }

  private static void setSlot(final SObject obj, final String slotName,
      final Object value, final MixinDefinition classDef) {
    SlotDefinition slot = (SlotDefinition) classDef.getInstanceDispatchables().get(
        Symbols.symbolFor(slotName));
    slot.setValueDuringBootstrap(obj, value);
  }

  private int handlePromiseResult(final SPromise promise) {
    // This is an attempt to prevent to get stuck indeterminately.
    // We check whether there is activity on any of the pools.
    // And, we exit when either the main promise is resolved, or an exit was requested.
    int emptyFJPool = 0;
    while (emptyFJPool < 120) {
      if (promise.isCompleted()) {
        if (promise.isErroredUnsync()) {
          return Launcher.EXIT_WITH_ERROR;
        }
        return vm.lastExitCode();
      }

      if (vm.shouldExit()) {
        return vm.lastExitCode();
      }

      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {}

      // never timeout when debugging
      if (vm.isPoolIdle() && !VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
        emptyFJPool++;
      } else {
        emptyFJPool = 0;
      }
    }

    assert !vm.shouldExit();
    TracingActors.ReplayActor.printMissingMessages();
    Output.errorPrintln(
        "VM seems to have exited prematurely. The actor pool has been idle for "
            + emptyFJPool + " checks in a row.");
    return Launcher.EXIT_WITH_ERROR;
  }

  public void releaseMainThread(final int errorCode) {
    mainThreadCompleted.complete(errorCode);
  }

  @TruffleBoundary
  public int executeApplication(final SObjectWithoutFields vmMirror, final Actor mainActor) {
    mainThreadCompleted = new CompletableFuture<>();

    ObjectTransitionSafepoint.INSTANCE.register();
    Object platform = platformModule.instantiateObject(platformClass, vmMirror);
    ObjectTransitionSafepoint.INSTANCE.unregister();

    SSymbol start = Symbols.symbolFor("start");
    SourceSection source;

    try {
      // might fail if module doesn't have a #start method
      SInvokable disp = (SInvokable) platformModule.getInstanceDispatchables().get(start);
      source = disp.getSourceSection();
    } catch (Exception e) {
      source = SomLanguage.getSyntheticSource("",
          "ObjectSystem.executeApplication").createSection(1);
    }

    DirectMessage msg = new DirectMessage(mainActor, start,
        new Object[] {platform}, mainActor,
        null, EventualSendNode.createOnReceiveCallTargetForVMMain(
            start, 1, source, mainThreadCompleted, compiler.getLanguage()));
    mainActor.sendInitialStartMessage(msg, vm.getActorPool());

    try {
      Object result = mainThreadCompleted.get();

      if (result instanceof Long || result instanceof Integer) {
        int exitCode = (result instanceof Long) ? (int) (long) result : (int) result;
        return exitCode;
      } else if (result instanceof SPromise) {
        return handlePromiseResult((SPromise) result);
      } else {
        Output.errorPrintln("The application's #main: method returned a " + result.toString()
            + ", but it needs to return a Promise or Integer as return value.");
        return Launcher.EXIT_WITH_ERROR;
      }
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
      return Launcher.EXIT_WITH_ERROR;
    }
  }

  @TruffleBoundary
  public Object execute(final String selector) {
    SInvokable method = (SInvokable) platformClass.getSOMClass().lookupMessage(
        Symbols.symbolFor(selector), AccessModifier.PUBLIC);
    try {
      ObjectTransitionSafepoint.INSTANCE.register();
      return method.invoke(new Object[] {platformClass});
    } finally {
      ObjectTransitionSafepoint.INSTANCE.unregister();
    }
  }
}
