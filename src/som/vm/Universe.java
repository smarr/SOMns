/**
 * Copyright (c) 2013 Stefan Marr,   stefan.marr@vub.ac.be
 * Copyright (c) 2009 Michael Haupt, michael.haupt@hpi.uni-potsdam.de
 * Software Architecture Group, Hasso Plattner Institute, Potsdam, Germany
 * http://www.hpi.uni-potsdam.de/swa/
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package som.vm;

import static som.vm.constants.Classes.arrayClass;
import static som.vm.constants.Classes.booleanClass;
import static som.vm.constants.Classes.classClass;
import static som.vm.constants.Classes.doubleClass;
import static som.vm.constants.Classes.integerClass;
import static som.vm.constants.Classes.metaclassClass;
import static som.vm.constants.Classes.methodClass;
import static som.vm.constants.Classes.nilClass;
import static som.vm.constants.Classes.objectClass;
import static som.vm.constants.Classes.primitiveClass;
import static som.vm.constants.Classes.stringClass;
import static som.vm.constants.Classes.symbolClass;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.StringTokenizer;

import som.compiler.Disassembler;
import som.interpreter.Invokable;
import som.interpreter.TruffleCompiler;
import som.vm.constants.Blocks;
import som.vm.constants.Globals;
import som.vm.constants.Nil;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SInvokable.SMethod;
import som.vmobjects.SInvokable.SPrimitive;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.MaterializedFrame;

public final class Universe {

  /**
   * Associations are handles for globals with a fixed
   * SSymbol and a mutable value.
   */
  public static final class Association {
    private final SSymbol    key;
    @CompilationFinal private Object  value;

    public Association(final SSymbol key, final Object value) {
      this.key   = key;
      this.value = value;
    }

    public SSymbol getKey() {
      return key;
    }

    public Object getValue() {
      return value;
    }

    public void setValue(final Object value) {
      TruffleCompiler.transferToInterpreterAndInvalidate("Changed global");
      this.value = value;
    }
  }

  public static void main(final String[] arguments) {
    Universe u = current();

    try {
      u.interpret(arguments);
      u.exit(0);
    } catch (IllegalStateException e) {
      errorExit(e.getMessage());
    }
  }

  public Object interpret(String[] arguments) {
    // Check for command line switches
    arguments = handleArguments(arguments);

    // Initialize the known universe
    return execute(arguments);
  }

  private Universe() {
    this.truffleRuntime = Truffle.getRuntime();
    this.globals      = new HashMap<SSymbol, Association>();
    this.symbolTable  = new HashMap<>();
    this.avoidExit    = false;
    this.alreadyInitialized = false;
    this.lastExitCode = 0;

    this.blockClasses = new SClass[4];
  }

  public TruffleRuntime getTruffleRuntime() {
    return truffleRuntime;
  }

  @SlowPath
  public void exit(final int errorCode) {
    // Exit from the Java system
    if (!avoidExit) {
      System.exit(errorCode);
    } else {
      lastExitCode = errorCode;
    }
  }

  public int lastExitCode() {
    return lastExitCode;
  }

  @SlowPath
  public static void errorExit(final String message) {
    errorPrintln("Runtime Error: " + message);
    current().exit(1);
  }

  @SlowPath
  public String[] handleArguments(String[] arguments) {
    boolean gotClasspath = false;
    String[] remainingArgs = new String[arguments.length];
    int cnt = 0;

    for (int i = 0; i < arguments.length; i++) {
      if (arguments[i].equals("-cp")) {
        if (i + 1 >= arguments.length) {
          printUsageAndExit();
        }
        setupClassPath(arguments[i + 1]);
        // Checkstyle: stop
        ++i; // skip class path
        // Checkstyle: resume
        gotClasspath = true;
      } else if (arguments[i].equals("-d")) {
        printAST = true;
      } else {
        remainingArgs[cnt++] = arguments[i];
      }
    }

    if (!gotClasspath) {
      // Get the default class path of the appropriate size
      classPath = setupDefaultClassPath(0);
    }

    // Copy the remaining elements from the original array into the new
    // array
    arguments = new String[cnt];
    System.arraycopy(remainingArgs, 0, arguments, 0, cnt);

    // check remaining args for class paths, and strip file extension
    for (int i = 0; i < arguments.length; i++) {
      String[] split = getPathClassExt(arguments[i]);

      if (!("".equals(split[0]))) { // there was a path
        String[] tmp = new String[classPath.length + 1];
        System.arraycopy(classPath, 0, tmp, 1, classPath.length);
        tmp[0] = split[0];
        classPath = tmp;
      }
      arguments[i] = split[1];
    }

    return arguments;
  }

  @SlowPath
  // take argument of the form "../foo/Test.som" and return
  // "../foo", "Test", "som"
  private String[] getPathClassExt(final String arg) {
    File file = new File(arg);

    String path = file.getParent();
    StringTokenizer tokenizer = new StringTokenizer(file.getName(), ".");

    if (tokenizer.countTokens() > 2) {
      errorPrintln("Class with . in its name?");
      exit(1);
    }

    String[] result = new String[3];
    result[0] = (path == null) ? "" : path;
    result[1] = tokenizer.nextToken();
    result[2] = tokenizer.hasMoreTokens() ? tokenizer.nextToken() : "";

    return result;
  }

  @SlowPath
  public void setupClassPath(final String cp) {
    // Create a new tokenizer to split up the string of directories
    StringTokenizer tokenizer = new StringTokenizer(cp, File.pathSeparator);

    // Get the default class path of the appropriate size
    classPath = setupDefaultClassPath(tokenizer.countTokens());

    // Get the directories and put them into the class path array
    for (int i = 0; tokenizer.hasMoreTokens(); i++) {
      classPath[i] = tokenizer.nextToken();
    }
  }

  @SlowPath
  private String[] setupDefaultClassPath(final int directories) {
    // Get the default system class path
    String systemClassPath = System.getProperty("system.class.path");

    // Compute the number of defaults
    int defaults = (systemClassPath != null) ? 2 : 1;

    // Allocate an array with room for the directories and the defaults
    String[] result = new String[directories + defaults];

    // Insert the system class path into the defaults section
    if (systemClassPath != null) {
      result[directories] = systemClassPath;
    }

    // Insert the current directory into the defaults section
    result[directories + defaults - 1] = ".";

    // Return the class path
    return result;
  }

  @SlowPath
  private void printUsageAndExit() {
    // Print the usage
    println("Usage: som [-options] [args...]                          ");
    println("                                                         ");
    println("where options include:                                   ");
    println("    -cp <directories separated by " + File.pathSeparator + ">");
    println("                  set search path for application classes");
    println("    -d            enable disassembling");

    // Exit
    System.exit(0);
  }

  /**
   * Start interpretation by sending the selector to the given class. This is
   * mostly meant for testing currently.
   *
   * @param className
   * @param selector
   * @return
   */
  public Object interpret(final String className, final String selector) {
    initializeObjectSystem();

    SClass clazz = loadClass(symbolFor(className));

    // Lookup the initialize invokable on the system class
    SInvokable initialize = clazz.getSOMClass().lookupInvokable(
        symbolFor(selector));
    return initialize.invoke(clazz);
  }

  private Object execute(final String[] arguments) {
    initializeObjectSystem();

    // Start the shell if no filename is given
    if (arguments.length == 0) {
      Shell shell = new Shell(this);
      return shell.start();
    }

    // Lookup the initialize invokable on the system class
    SInvokable initialize = systemClass.
        lookupInvokable(symbolFor("initialize:"));

    return initialize.invoke(new Object[] {systemObject, arguments});
  }

  @SlowPath
  protected void initializeObjectSystem() {
    if (alreadyInitialized) {
      return;
    } else {
      alreadyInitialized = true;
    }

    // Allocate the nil object
    SObject nilObject = Nil.nilObject;

    // Setup the class reference for the nil object
    nilObject.setClass(nilClass);

    // Initialize the system classes.
    initializeSystemClass(objectClass,            null, "Object");
    initializeSystemClass(classClass,      objectClass, "Class");
    initializeSystemClass(metaclassClass,   classClass, "Metaclass");
    initializeSystemClass(nilClass,        objectClass, "Nil");
    initializeSystemClass(arrayClass,      objectClass, "Array");
    initializeSystemClass(methodClass,     objectClass, "Method");
    initializeSystemClass(symbolClass,     objectClass, "Symbol");
    initializeSystemClass(integerClass,    objectClass, "Integer");
    initializeSystemClass(primitiveClass,  objectClass, "Primitive");
    initializeSystemClass(stringClass,     objectClass, "String");
    initializeSystemClass(doubleClass,     objectClass, "Double");
    initializeSystemClass(booleanClass,    objectClass, "Boolean");

    trueClass  = newSystemClass();
    falseClass = newSystemClass();

    initializeSystemClass(trueClass,      booleanClass, "True");
    initializeSystemClass(falseClass,     booleanClass, "False");

    // Load methods and fields into the system classes
    loadSystemClass(objectClass);
    loadSystemClass(classClass);
    loadSystemClass(metaclassClass);
    loadSystemClass(nilClass);
    loadSystemClass(arrayClass);
    loadSystemClass(methodClass);
    loadSystemClass(symbolClass);
    loadSystemClass(integerClass);
    loadSystemClass(primitiveClass);
    loadSystemClass(stringClass);
    loadSystemClass(doubleClass);
    loadSystemClass(booleanClass);
    loadSystemClass(trueClass);
    loadSystemClass(falseClass);

    // Load the generic block class
    blockClasses[0] = loadClass(symbolFor("Block"));

    // Setup the true and false objects
    trueObject  = newInstance(trueClass);
    falseObject = newInstance(falseClass);

    // Load the system class and create an instance of it
    systemClass  = loadClass(symbolFor("System"));
    systemObject = newInstance(systemClass);

    // Put special objects into the dictionary of globals
    setGlobal("nil",    nilObject);
    setGlobal("true",   trueObject);
    setGlobal("false",  falseObject);
    setGlobal("system", systemObject);

    // Load the remaining block classes
    loadBlockClass(1);
    loadBlockClass(2);
    loadBlockClass(3);

    if (Globals.trueObject != trueObject) {
      errorExit("Initialization went wrong for class Globals");
    }

    if (Blocks.blockClass1 != blockClasses[1]) {
      errorExit("Initialization went wrong for class Blocks");
    }
    objectSystemInitialized = true;
  }

  @SlowPath
  public SSymbol symbolFor(final String string) {
    String interned = string.intern();
    // Lookup the symbol in the symbol table
    SSymbol result = symbolTable.get(interned);
    if (result != null) { return result; }

    return newSymbol(interned);
  }

  public static SBlock newBlock(final SMethod method, final MaterializedFrame context) {
    return SBlock.create(method, context);
  }

  @SlowPath
  public SClass newClass(final SClass classClass) {
    return new SClass(classClass);
  }

  @SlowPath
  public static SInvokable newMethod(final SSymbol signature,
      final Invokable truffleInvokable, final boolean isPrimitive,
      final SMethod[] embeddedBlocks) {
    if (isPrimitive) {
      return new SPrimitive(signature, truffleInvokable);
    } else {
      return new SMethod(signature, truffleInvokable, embeddedBlocks);
    }
  }

  public static SObject newInstance(final SClass instanceClass) {
    return SObject.create(instanceClass);
  }

  @SlowPath
  public static SClass newMetaclassClass() {
    // Allocate the metaclass classes
    SClass result = new SClass(0);
    result.setClass(new SClass(0));

    // Setup the metaclass hierarchy
    result.getSOMClass().setClass(result);
    return result;
  }

  private SSymbol newSymbol(final String string) {
    SSymbol result = new SSymbol(string);
    symbolTable.put(string, result);
    return result;
  }

  @SlowPath
  public static SClass newSystemClass() {
    // Allocate the new system class
    SClass systemClass = new SClass(0);

    // Setup the metaclass hierarchy
    systemClass.setClass(new SClass(0));
    systemClass.getSOMClass().setClass(metaclassClass);

    // Return the freshly allocated system class
    return systemClass;
  }

  @SlowPath
  public void initializeSystemClass(final SClass systemClass, final SClass superClass,
      final String name) {
    // Initialize the superclass hierarchy
    if (superClass != null) {
      systemClass.setSuperClass(superClass);
      systemClass.getSOMClass().setSuperClass(superClass.getSOMClass());
    } else {
      systemClass.getSOMClass().setSuperClass(classClass);
    }

    // Initialize the array of instance fields
    systemClass.setInstanceFields(new SSymbol[0]);
    systemClass.getSOMClass().setInstanceFields(new SSymbol[0]);

    // Initialize the array of instance invokables
    systemClass.setInstanceInvokables(new SInvokable[0]);
    systemClass.getSOMClass().setInstanceInvokables(new SInvokable[0]);

    // Initialize the name of the system class
    systemClass.setName(symbolFor(name));
    systemClass.getSOMClass().setName(symbolFor(name + " class"));

    // Insert the system class into the dictionary of globals
    setGlobal(systemClass.getName(), systemClass);
  }

  @SlowPath
  public boolean hasGlobal(final SSymbol name) {
    return globals.containsKey(name);
  }

  @SlowPath
  public Object getGlobal(final SSymbol name) {
    Association assoc = globals.get(name);
    if (assoc == null) {
      return null;
    }
    return assoc.getValue();
  }

  @SlowPath
  public Association getGlobalsAssociation(final SSymbol name) {
    return globals.get(name);
  }

  public void setGlobal(final String name, final Object value) {
    setGlobal(symbolFor(name), value);
  }

  @SlowPath
  public void setGlobal(final SSymbol name, final Object value) {
    Association assoc = globals.get(name);
    if (assoc == null) {
      assoc = new Association(name, value);
      globals.put(name, assoc);
    } else {
      assoc.setValue(value);
    }
  }

  public SClass getBlockClass(final int numberOfArguments) {
    SClass result = blockClasses[numberOfArguments];
    assert result != null || numberOfArguments == 0;
    return result;
  }

  private void loadBlockClass(final int numberOfArguments) {
    // Compute the name of the block class with the given number of
    // arguments
    SSymbol name = symbolFor("Block" + numberOfArguments);

    assert getGlobal(name) == null;

    // Get the block class for blocks with the given number of arguments
    SClass result = loadClass(name, null);

    // Add the appropriate value primitive to the block class
    result.addInstancePrimitive(SBlock.getEvaluationPrimitive(
        numberOfArguments, this, result));

    // Insert the block class into the dictionary of globals
    setGlobal(name, result);

    blockClasses[numberOfArguments] = result;
  }

  @SlowPath
  public SClass loadClass(final SSymbol name) {
    // Check if the requested class is already in the dictionary of globals
    SClass result = (SClass) getGlobal(name);
    if (result != null) { return result; }

    result = loadClass(name, null);
    loadPrimitives(result, false);

    setGlobal(name, result);

    return result;
  }

  private void loadPrimitives(final SClass result, final boolean isSystemClass) {
    if (result == null) { return; }

    // Load primitives if class defines them, or try to load optional
    // primitives defined for system classes.
    if (result.hasPrimitives() || isSystemClass) {
      result.loadPrimitives(!isSystemClass);
    }
  }

  @SlowPath
  private void loadSystemClass(final SClass systemClass) {
    // Load the system class
    SClass result = loadClass(systemClass.getName(), systemClass);

    if (result == null) {
      throw new IllegalStateException(systemClass.getName().getString()
          + " class could not be loaded. "
          + "It is likely that the class path has not been initialized properly. "
          + "Please set system property 'system.class.path' or "
          + "pass the '-cp' command-line parameter.");
    }

    loadPrimitives(result, true);
  }

  @SlowPath
  private SClass loadClass(final SSymbol name, final SClass systemClass) {
    // Try loading the class from all different paths
    for (String cpEntry : classPath) {
      try {
        // Load the class from a file and return the loaded class
        SClass result = som.compiler.SourcecodeCompiler.compileClass(cpEntry,
            name.getString(), systemClass, this);
        if (printAST) {
          Disassembler.dump(result.getSOMClass());
          Disassembler.dump(result);
        }
        return result;

      } catch (IOException e) {
        // Continue trying different paths
      }
    }

    // The class could not be found.
    return null;
  }

  @SlowPath
  public SClass loadShellClass(final String stmt) throws IOException {
    // Load the class from a stream and return the loaded class
    SClass result = som.compiler.SourcecodeCompiler.compileClass(stmt, null,
        this);
    if (printAST) { Disassembler.dump(result); }
    return result;
  }

  public void setAvoidExit(final boolean value) {
    avoidExit = value;
  }

  @SlowPath
  public static void errorPrint(final String msg) {
    // Checkstyle: stop
    System.err.print(msg);
    // Checkstyle: resume
  }

  @SlowPath
  public static void errorPrintln(final String msg) {
    // Checkstyle: stop
    System.err.println(msg);
    // Checkstyle: resume
  }

  @SlowPath
  public static void errorPrintln() {
    // Checkstyle: stop
    System.err.println();
    // Checkstyle: resume
  }

  @SlowPath
  public static void print(final String msg) {
    // Checkstyle: stop
    System.out.print(msg);
    // Checkstyle: resume
  }

  @SlowPath
  public static void println(final String msg) {
    // Checkstyle: stop
    System.out.println(msg);
    // Checkstyle: resume
  }

  @SlowPath
  public static void println() {
    // Checkstyle: stop
    System.out.println();
    // Checkstyle: resume
  }

  public SObject getTrueObject()   { return trueObject; }
  public SObject getFalseObject()  { return falseObject; }
  public SObject getSystemObject() { return systemObject; }

  public SClass getTrueClass()   { return trueClass; }
  public SClass getFalseClass()  { return falseClass; }
  public SClass getSystemClass() { return systemClass; }

  @CompilationFinal private SObject trueObject;
  @CompilationFinal private SObject falseObject;
  @CompilationFinal private SObject systemObject;

  @CompilationFinal private SClass  trueClass;
  @CompilationFinal private SClass  falseClass;
  @CompilationFinal private SClass  systemClass;

  private final HashMap<SSymbol, Association>   globals;

  private String[]                              classPath;
  @CompilationFinal private boolean             printAST;

  private final TruffleRuntime                  truffleRuntime;

  private final HashMap<String, SSymbol>        symbolTable;

  // TODO: this is not how it is supposed to be... it is just a hack to cope
  //       with the use of system.exit in SOM to enable testing
  @CompilationFinal private boolean             avoidExit;
  private int                                   lastExitCode;

  // Optimizations
  private final SClass[] blockClasses;

  // Latest instance
  // WARNING: this is problematic with multiple interpreters in the same VM...
  @CompilationFinal private static Universe current;
  @CompilationFinal private boolean alreadyInitialized;

  @CompilationFinal private boolean objectSystemInitialized = false;

  public boolean isObjectSystemInitialized() {
    return objectSystemInitialized;
  }

  public static Universe current() {
    if (current == null) {
      current = new Universe();
    }
    return current;
  }
}
