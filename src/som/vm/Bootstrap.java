package som.vm;

import static som.vm.constants.Classes.metaclassClass;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import som.compiler.AccessModifier;
import som.compiler.ClassDefinition;
import som.compiler.MethodBuilder;
import som.compiler.SourcecodeCompiler;
import som.interpreter.Invokable;
import som.interpreter.Primitive;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.primitives.HashPrimFactory;
import som.vm.constants.Classes;
import som.vm.constants.KernelObj;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SInvokable.SPrimitive;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.NodeUtil;


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

  private static HashMap<SSymbol, SInvokable> constructVmMirrorPrimitives() {
    /// XXX: SKETCH PRIMITIVE CONSTRUCTION
    MethodBuilder prim = new MethodBuilder(null);
    prim.addArgumentIfAbsent("self");
    prim.addArgumentIfAbsent("obj");

    ExpressionNode primNode = HashPrimFactory.create(
        prim.getReadNode("obj", null));

    Invokable objHashcode = new Primitive(primNode,
        prim.getCurrentMethodScope().getFrameDescriptor(),
        NodeUtil.cloneNode(primNode));


    HashMap<SSymbol, SInvokable> vmMirrorMethods = new HashMap<>();
    vmMirrorMethods.put(Symbols.symbolFor("objHashcode:"),
        new SPrimitive(Symbols.symbolFor("objHashcode:"), objHashcode));
    return vmMirrorMethods;
  }

  private static SObject constructVmMirror() {
    HashMap<SSymbol, SInvokable> vmMirrorMethods = constructVmMirrorPrimitives();
    ClassDefinition vmMirrorDef = new ClassDefinition(
        Symbols.symbolFor("VmMirror"), null, vmMirrorMethods, null, null, null);
    SClass vmMirrorClass = vmMirrorDef.instantiateClass(null, null);
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


    // Top doesn't have any methods or slots, so outer and super can be null
       topDef.initializeClass(Classes.topClass, null);
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

    platformClass = platformModule.instantiateClass(Nil.nilObject, Classes.valueClass);
    SClass kernelClass = kernelModule.instantiateClass(Nil.nilObject, Classes.valueClass);
    KernelObj.kernel.setClass(kernelClass);


    // TODO:: cleanup ??? reuse comments?
    // we need:
    //   vmMirror -> Object & Class -> Top
    //   Platform class -super> Class & -class> Metaclass -class> Metaclass class
    //
    // in the end, we need to be able instantiate the outer class of the Platform module
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
