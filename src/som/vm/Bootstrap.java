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
import som.primitives.HashPrimFactory;
import som.vm.constants.Classes;
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
        prim.getCurrentLexicalScope().getFrameDescriptor(),
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
  public static SClass newMetaclassClass() {
    SClass metaclassClass      = new SClass(); // class obj for "Metaclass"
    SClass metaclassClassClass = new SClass(); // class obj for "Metaclass class"
    metaclassClass.setClass(metaclassClassClass);

    metaclassClass.setName(Symbols.symbolFor("Metaclass"));
    metaclassClassClass.setName(Symbols.symbolFor("Metaclass class"));

    // Connect the metaclass hierarchy
    metaclassClass.getSOMClass().setClass(metaclassClass);
    return metaclassClass;
  }

  public static SClass newEmptyClassWithItsClass(final String name) {
    SClass clazz = new SClass();
    clazz.setName(Symbols.symbolFor(name));

    // Setup the metaclass hierarchy
    SClass clazzClazz = new SClass();
    clazzClazz.setName(Symbols.symbolFor(name + " class"));
    clazz.setClass(clazzClazz);
    clazz.getSOMClass().setClass(metaclassClass);

    // Return the freshly allocated system class
    return clazz;
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

    SClass kernelClass = new SClass();
    SObject kernelObj  = new SObject(kernelModule.getNumberOfSlots());
    kernelClass.setName(Symbols.symbolFor("Kernel"));


    // Top doesn't have any methods or slots, so outer and super can be null
       topDef.initializeClass(null, Classes.topClass, null);
     thingDef.initializeClass(kernelObj, Classes.thingClass,  Classes.topClass);
     valueDef.initializeClass(kernelObj, Classes.valueClass,  Classes.thingClass);
    objectDef.initializeClass(kernelObj, Classes.objectClass, Classes.thingClass);
     classDef.initializeClass(kernelObj, Classes.classClass,  Classes.objectClass);
 metaclassDef.initializeClass(kernelObj, Classes.metaclassClass, Classes.classClass);
       nilDef.initializeClass(kernelObj, Classes.nilClass,    Classes.valueClass);

      arrayDef.initializeClass(kernelObj, Classes.arrayClass,   Classes.objectClass);
    integerDef.initializeClass(kernelObj, Classes.integerClass, Classes.valueClass);
     stringDef.initializeClass(kernelObj, Classes.stringClass,  Classes.valueClass);
     doubleDef.initializeClass(kernelObj, Classes.doubleClass,  Classes.valueClass);
    booleanDef.initializeClass(kernelObj, Classes.booleanClass, Classes.valueClass);
     symbolDef.initializeClass(kernelObj, Classes.symbolClass,  Classes.stringClass);

     blockDef.initializeClass(kernelObj, Classes.blockClass,  Classes.objectClass);
    block1Def.initializeClass(kernelObj, Classes.blockClass1, Classes.blockClass);
    block2Def.initializeClass(kernelObj, Classes.blockClass2, Classes.blockClass1);
    block3Def.initializeClass(kernelObj, Classes.blockClass3, Classes.blockClass2);

    Nil.nilObject.setClass(Classes.nilClass);

    platformClass = platformModule.instantiateClass(Nil.nilObject, Classes.valueClass);

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
    SInvokable method = platformClass.getSOMClass().lookupInvokable(
        Symbols.symbolFor(selector));
    return method.invoke(platformClass);
  }
}
