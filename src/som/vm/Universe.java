/**
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

import java.io.IOException;
import java.util.HashMap;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;

import som.compiler.Disassembler;
import som.vmobjects.Array;
import som.vmobjects.BigInteger;
import som.vmobjects.Block;
import som.vmobjects.Class;
import som.vmobjects.Double;
import som.vmobjects.Integer;
import som.vmobjects.Invokable;
import som.vmobjects.Method;
import som.vmobjects.Object;
import som.vmobjects.String;
import som.vmobjects.Symbol;

public class Universe {
  
  public static void main(java.lang.String[] arguments) {
    // Create Universe
    Universe u = new Universe();
    
    // Start interpretation
    u.interpret(arguments);
    
    // Exit with error code 0
    u.exit(0);
  }
  
  public void interpret(java.lang.String[] arguments) {
    // Check for command line switches
    arguments = handleArguments(arguments);

    // Initialize the known universe
    initialize(arguments);
  }

  static { /* static initializer */
    pathSeparator = System.getProperty("path.separator");
    fileSeparator = System.getProperty("file.separator");
  }

  public Universe() {
    this.truffleRuntime = Truffle.getRuntime();
    this.symbolTable  = new SymbolTable();
    this.avoidExit    = false;
    this.lastExitCode = 0;
  }
  
  public Universe(boolean avoidExit) {
    this.truffleRuntime = Truffle.getRuntime();
    this.symbolTable  = new SymbolTable();
    this.avoidExit    = avoidExit;
    this.lastExitCode = 0;
  }

  public TruffleRuntime getTruffleRuntime() {
    return truffleRuntime;
  }

  public void exit(int errorCode) {
    // Exit from the Java system
    if (!avoidExit) {
      System.exit(errorCode);
    }
    else {
      lastExitCode = errorCode;
    }
  }
  
  public int lastExitCode() {
    return lastExitCode;
  }

  public void errorExit(java.lang.String message) {
    System.out.println("Runtime Error: " + message);
    exit(1);
  }

  private java.lang.String[] handleArguments(java.lang.String[] arguments) {
    boolean gotClasspath = false;
    java.lang.String[] remainingArgs = new java.lang.String[arguments.length];
    int cnt = 0;

    for (int i = 0; i < arguments.length; i++) {
      if (arguments[i].equals("-cp")) {
        if (i + 1 >= arguments.length) printUsageAndExit();
        setupClassPath(arguments[i + 1]);
        ++i; // skip class path
        gotClasspath = true;
      }
      else if (arguments[i].equals("-d")) {
        printAST = true;
      }
      else {
        remainingArgs[cnt++] = arguments[i];
      }
    }

    if (!gotClasspath) {
      // Get the default class path of the appropriate size
      classPath = setupDefaultClassPath(0);
    }

    // Copy the remaining elements from the original array into the new
    // array
    arguments = new java.lang.String[cnt];
    System.arraycopy(remainingArgs, 0, arguments, 0, cnt);

    // check remaining args for class paths, and strip file extension
    for (int i = 0; i < arguments.length; i++) {
      java.lang.String[] split = getPathClassExt(arguments[i]);

      if (split[0] != "") { // there was a path
        java.lang.String[] tmp = new java.lang.String[classPath.length + 1];
        System.arraycopy(classPath, 0, tmp, 1, classPath.length);
        tmp[0] = split[0];
        classPath = tmp;
      }
      arguments[i] = split[1];
    }

    return arguments;
  }

  // take argument of the form "../foo/Test.som" and return
  // "../foo", "Test", "som"
  private java.lang.String[] getPathClassExt(java.lang.String arg) {
    // Create a new tokenizer to split up the string of dirs
    java.util.StringTokenizer tokenizer = new java.util.StringTokenizer(arg,
        fileSeparator, true);

    java.lang.String cp = "";

    while (tokenizer.countTokens() > 2) {
      cp = cp + tokenizer.nextToken();
    }
    if (tokenizer.countTokens() == 2) {
      tokenizer.nextToken(); // throw out delimiter
    }

    java.lang.String file = tokenizer.nextToken();

    tokenizer = new java.util.StringTokenizer(file, ".");

    if (tokenizer.countTokens() > 2) {
      System.out.println("Class with . in its name?");
      exit(1);
    }

    java.lang.String[] result = new java.lang.String[3];
    result[0] = cp;
    result[1] = tokenizer.nextToken();
    result[2] = tokenizer.hasMoreTokens() ? tokenizer.nextToken() : "";
    return result;
  }

  private void setupClassPath(java.lang.String cp) {
    // Create a new tokenizer to split up the string of directories
    java.util.StringTokenizer tokenizer = new java.util.StringTokenizer(cp,
        pathSeparator);

    // Get the default class path of the appropriate size
    classPath = setupDefaultClassPath(tokenizer.countTokens());

    // Get the dictories and put them into the class path array
    for (int i = 0; tokenizer.hasMoreTokens(); i++) {
      classPath[i] = tokenizer.nextToken();
    }
  }

  private java.lang.String[] setupDefaultClassPath(int directories) {
    // Get the default system class path
    java.lang.String systemClassPath = System.getProperty("system.class.path");

    // Compute the number of defaults
    int defaults = (systemClassPath != null) ? 2 : 1;

    // Allocate an array with room for the directories and the defaults
    java.lang.String[] result = new java.lang.String[directories + defaults];

    // Insert the system class path into the defaults section
    if (systemClassPath != null) {
      result[directories] = systemClassPath;
    }

    // Insert the current directory into the defaults section
    result[directories + defaults - 1] = ".";

    // Return the class path
    return result;
  }

  private void printUsageAndExit() {
    // Print the usage
    System.out
        .println("Usage: som [-options] [args...]                          ");
    System.out
        .println("                                                         ");
    System.out
        .println("where options include:                                   ");
    System.out.println("    -cp <directories separated by " + pathSeparator
        + ">");
    System.out
        .println("                  set search path for application classes");
    System.out.println("    -d            enable disassembling");

    // Exit
    System.exit(0);
  }

  private void initialize(java.lang.String[] arguments) {
    // Allocate the nil object
    nilObject = new Object(null);

    // Allocate the Metaclass classes
    metaclassClass = newMetaclassClass();

    // Allocate the rest of the system classes
    objectClass = newSystemClass();
    nilClass = newSystemClass();
    classClass = newSystemClass();
    arrayClass = newSystemClass();
    symbolClass = newSystemClass();
    methodClass = newSystemClass();
    integerClass = newSystemClass();
    bigintegerClass = newSystemClass();
    /** REMOVED FOR TRUFFLE   frameClass = newSystemClass(); */
    primitiveClass = newSystemClass();
    stringClass = newSystemClass();
    doubleClass = newSystemClass();

    // Setup the class reference for the nil object
    nilObject.setClass(nilClass);

    // Initialize the system classes.
    initializeSystemClass(objectClass, null, "Object");
    initializeSystemClass(classClass, objectClass, "Class");
    initializeSystemClass(metaclassClass, classClass, "Metaclass");
    initializeSystemClass(nilClass, objectClass, "Nil");
    initializeSystemClass(arrayClass, objectClass, "Array");
    initializeSystemClass(methodClass, arrayClass, "Method");
    initializeSystemClass(symbolClass, objectClass, "Symbol");
    initializeSystemClass(integerClass, objectClass, "Integer");
    initializeSystemClass(bigintegerClass, objectClass, "BigInteger");
    /** REMOVED FOR TRUFFLE initializeSystemClass(frameClass, arrayClass, "Frame"); */
    initializeSystemClass(primitiveClass, objectClass, "Primitive");
    initializeSystemClass(stringClass, objectClass, "String");
    initializeSystemClass(doubleClass, objectClass, "Double");

    // Load methods and fields into the system classes
    loadSystemClass(objectClass);
    loadSystemClass(classClass);
    loadSystemClass(metaclassClass);
    loadSystemClass(nilClass);
    loadSystemClass(arrayClass);
    loadSystemClass(methodClass);
    loadSystemClass(symbolClass);
    loadSystemClass(integerClass);
    loadSystemClass(bigintegerClass);
    /** REMOVED FOR TRUFFLE loadSystemClass(frameClass); */
    loadSystemClass(primitiveClass);
    loadSystemClass(stringClass);
    loadSystemClass(doubleClass);

    // Load the generic block class
    blockClass = loadClass(symbolFor("Block"));

    // Setup the true and false objects
    trueObject = newInstance(loadClass(symbolFor("True")));
    falseObject = newInstance(loadClass(symbolFor("False")));

    // Load the system class and create an instance of it
    systemClass = loadClass(symbolFor("System"));
    Object systemObject = newInstance(systemClass);

    // Put special objects and classes into the dictionary of globals
    setGlobal(symbolFor("nil"),    nilObject);
    setGlobal(symbolFor("true"),   trueObject);
    setGlobal(symbolFor("false"),  falseObject);
    setGlobal(symbolFor("system"), systemObject);
    setGlobal(symbolFor("System"), systemClass);
    setGlobal(symbolFor("Block"),  blockClass);

    // Start the shell if no filename is given
    
    // TODO: add support for Shell
    
    /** if (arguments.length == 0) {
      Shell shell = new Shell(this, interpreter);
      shell.setBootstrapMethod(bootstrapMethod);
      shell.start();
      return;
    } */

    // Convert the arguments into an array
    Array argumentsArray = newArray(arguments);

    // Lookup the initialize invokable on the system class
    Method initialize = (Method) systemClass
        .lookupInvokable(symbolFor("initialize:"));

    // Invoke the initialize invokable
    initialize.invokeRoot(systemObject, new Object[] { argumentsArray });
  }

  public Symbol symbolFor(java.lang.String string) {
    // Lookup the symbol in the symbol table
    Symbol result = symbolTable.lookup(string);
    if (result != null) return result;

    // Create a new symbol and return it
    result = newSymbol(string);
    return result;
  }

  public Array newArray(int length) {
    // Allocate a new array and set its class to be the array class
    Array result = new Array(nilObject);
    result.setClass(arrayClass);

    // Set the number of indexable fields to the given value (length)
    result.setNumberOfIndexableFieldsAndClear(length, nilObject);

    // Return the freshly allocated array
    return result;
  }

  public Array newArray(java.util.List<?> list) {
    // Allocate a new array with the same length as the list
    Array result = newArray(list.size());

    // Copy all elements from the list into the array
    for (int i = 0; i < list.size(); i++) {
      result.setIndexableField(i, (Object) list.get(i));
    }

    // Return the allocated and initialized array
    return result;
  }

  public Array newArray(java.lang.String[] stringArray) {
    // Allocate a new array with the same length as the string array
    Array result = newArray(stringArray.length);

    // Copy all elements from the string array into the array
    for (int i = 0; i < stringArray.length; i++) {
      result.setIndexableField(i, newString(stringArray[i]));
    }

    // Return the allocated and initialized array
    return result;
  }

  public Block newBlock(Method method, VirtualFrame context, int arguments) {
    // Allocate a new block and set its class to be the block class
    Block result = new Block(nilObject);
    result.setClass(getBlockClass(arguments));

    // Set the method and context of block
    result.setMethod(method);
    result.setContext(context);

    // Return the freshly allocated block
    return result;
  }

  public Class newClass(Class classClass) {
    // Allocate a new class and set its class to be the given class class
    Class result = new Class(classClass.getNumberOfInstanceFields(), this);
    result.setClass(classClass);

    // Return the freshly allocated class
    return result;
  }

  /** REMOVED FOR TRUFFLE
  public Frame newFrame(Frame previousFrame, Method method) {
    // Allocate a new frame and set its class to be the frame class
    Frame result = new Frame(nilObject);
    result.setClass(frameClass);

    // Compute the maximum number of stack locations (including arguments,
    // locals and
    // extra buffer to support doesNotUnderstand) and set the number of
    // indexable fields accordingly
    int length = method.getNumberOfArguments()
        + method.getNumberOfLocals().getEmbeddedInteger()
        + method.getMaximumNumberOfStackElements().getEmbeddedInteger() + 2;
    result.setNumberOfIndexableFieldsAndClear(length, nilObject);

    // Set the method of the frame and the previous frame
    result.setMethod(method);
    if (previousFrame != null) result.setPreviousFrame(previousFrame);

    // Reset the stack pointer and the bytecode index
    result.resetStackPointer();
    result.setBytecodeIndex(0);

    // Return the freshly allocated frame
    return result;
  } */

  public Method newMethod(Symbol signature, som.interpreter.nodes.Method truffleInvokable, FrameDescriptor frameDescriptor) {
    // Allocate a new method and set its class to be the method class
    Method result = new Method(nilObject, truffleInvokable, frameDescriptor);
    result.setClass(methodClass);
    result.setSignature(signature);
    result.setNumberOfIndexableFieldsAndClear(0, nilObject);

    // Return the freshly allocated method
    return result;
  }

  public Object newInstance(Class instanceClass) {
    // Allocate a new instance and set its class to be the given class
    Object result = new Object(instanceClass.getNumberOfInstanceFields(),
        nilObject);
    result.setClass(instanceClass);

    // Return the freshly allocated instance
    return result;
  }

  public Integer newInteger(int value) {
    // Allocate a new integer and set its class to be the integer class
    Integer result = new Integer(nilObject);
    result.setClass(integerClass);

    // Set the embedded integer of the newly allocated integer
    result.setEmbeddedInteger(value);

    // Return the freshly allocated integer
    return result;
  }

  public BigInteger newBigInteger(java.math.BigInteger value) {
    // Allocate a new integer and set its class to be the integer class
    BigInteger result = new BigInteger(nilObject);
    result.setClass(bigintegerClass);

    // Set the embedded integer of the newly allocated integer
    result.setEmbeddedBiginteger(value);

    // Return the freshly allocated integer
    return result;
  }

  public BigInteger newBigInteger(long value) {
    // Allocate a new integer and set its class to be the integer class
    BigInteger result = new BigInteger(nilObject);
    result.setClass(bigintegerClass);

    // Set the embedded integer of the newly allocated integer
    result.setEmbeddedBiginteger(new java.math.BigInteger(new java.lang.Long(
        value).toString()));

    // Return the freshly allocated integer
    return result;
  }

  public Double newDouble(double value) {
    // Allocate a new integer and set its class to be the double class
    Double result = new Double(nilObject);
    result.setClass(doubleClass);

    // Set the embedded double of the newly allocated double
    result.setEmbeddedDouble(value);

    // Return the freshly allocated double
    return result;
  }

  public Class newMetaclassClass() {
    // Allocate the metaclass classes
    Class result = new Class(this);
    result.setClass(new Class(this));

    // Setup the metaclass hierarchy
    result.getSOMClass().setClass(result);

    // Return the freshly allocated metaclass class
    return result;
  }

  public String newString(java.lang.String embeddedString) {
    // Allocate a new string and set its class to be the string class
    String result = new String(nilObject);
    result.setClass(stringClass);

    // Put the embedded string into the new string
    result.setEmbeddedString(embeddedString);

    // Return the freshly allocated string
    return result;
  }

  public Symbol newSymbol(java.lang.String string) {
    // Allocate a new symbol and set its class to be the symbol class
    Symbol result = new Symbol(nilObject);
    result.setClass(symbolClass);

    // Put the string into the symbol
    result.setString(string);

    // Insert the new symbol into the symbol table
    symbolTable.insert(result);

    // Return the freshly allocated symbol
    return result;
  }

  public Class newSystemClass() {
    // Allocate the new system class
    Class systemClass = new Class(this);

    // Setup the metaclass hierarchy
    systemClass.setClass(new Class(this));
    systemClass.getSOMClass().setClass(metaclassClass);

    // Return the freshly allocated system class
    return systemClass;
  }

  public void initializeSystemClass(Class systemClass, Class superClass,
      java.lang.String name) {
    // Initialize the superclass hierarchy
    if (superClass != null) {
      systemClass.setSuperClass(superClass);
      systemClass.getSOMClass().setSuperClass(superClass.getSOMClass());
    }
    else {
      systemClass.getSOMClass().setSuperClass(classClass);
    }

    // Initialize the array of instance fields
    systemClass.setInstanceFields(newArray(0));
    systemClass.getSOMClass().setInstanceFields(newArray(0));

    // Initialize the array of instance invokables
    systemClass.setInstanceInvokables(newArray(0));
    systemClass.getSOMClass().setInstanceInvokables(newArray(0));

    // Initialize the name of the system class
    systemClass.setName(symbolFor(name));
    systemClass.getSOMClass().setName(symbolFor(name + " class"));

    // Insert the system class into the dictionary of globals
    setGlobal(systemClass.getName(), systemClass);
  }

  public Object getGlobal(Symbol name) {
    // Return the global with the given name if it's in the dictionary of
    // globals
    if (hasGlobal(name)) return (Object) globals.get(name);

    // Global not found
    return null;
  }

  public void setGlobal(Symbol name, Object value) {
    // Insert the given value into the dictionary of globals
    globals.put(name, value);
  }

  public boolean hasGlobal(Symbol name) {
    // Returns if the universe has a value for the global of the given name
    return globals.containsKey(name);
  }

  public Class getBlockClass() {
    // Get the generic block class
    return blockClass;
  }

  public Class getBlockClass(int numberOfArguments) {
    // Compute the name of the block class with the given number of
    // arguments
    Symbol name = symbolFor("Block"
        + java.lang.Integer.toString(numberOfArguments));

    // Lookup the specific block class in the dictionary of globals and
    // return it
    if (hasGlobal(name)) return (Class) getGlobal(name);

    // Get the block class for blocks with the given number of arguments
    Class result = loadClass(name, null);

    // Add the appropriate value primitive to the block class
    result.addInstancePrimitive(Block.getEvaluationPrimitive(numberOfArguments,
        this));

    // Insert the block class into the dictionary of globals
    setGlobal(name, result);

    // Return the loaded block class
    return result;
  }

  public Class loadClass(Symbol name) {
    // Check if the requested class is already in the dictionary of globals
    if (hasGlobal(name)) return (Class) getGlobal(name);

    // Load the class
    Class result = loadClass(name, null);

    // Load primitives (if necessary) and return the resulting class
    if (result != null && result.hasPrimitives()) result.loadPrimitives();
    return result;
  }

  public void loadSystemClass(Class systemClass) {
    // Load the system class
    Class result = loadClass(systemClass.getName(), systemClass);

    // Load primitives if necessary
    if (result.hasPrimitives()) result.loadPrimitives();
  }

  public Class loadClass(Symbol name, Class systemClass) {
    // Try loading the class from all different paths
    for (java.lang.String cpEntry : classPath) {
      try {
        // Load the class from a file and return the loaded class
        Class result = som.compiler.SourcecodeCompiler.compileClass(cpEntry
            + fileSeparator, name.getString(), systemClass, this);
        if (printAST) {
          Disassembler.dump(result.getSOMClass());
          Disassembler.dump(result);
        }
        return result;

      }
      catch (IOException e) {
        // Continue trying different paths
      }
    }

    // The class could not be found.
    return null;
  }

  public Class loadShellClass(java.lang.String stmt) throws IOException {
    // java.io.ByteArrayInputStream in = new
    // java.io.ByteArrayInputStream(stmt.getBytes());

    // Load the class from a stream and return the loaded class
    Class result = som.compiler.SourcecodeCompiler.compileClass(stmt, null,
        this);
    if (printAST) Disassembler.dump(result);
    return result;
  }

  public Object                                 nilObject;
  public Object                                 trueObject;
  public Object                                 falseObject;

  public Class                                  objectClass;
  public Class                                  classClass;
  public Class                                  metaclassClass;

  public Class                                  nilClass;
  public Class                                  integerClass;
  public Class                                  bigintegerClass;
  public Class                                  arrayClass;
  public Class                                  methodClass;
  public Class                                  symbolClass;
  /** REMOVED FOR TRUFFLE   public Class        frameClass; */
  public Class                                  primitiveClass;
  public Class                                  stringClass;
  public Class                                  systemClass;
  public Class                                  blockClass;
  public Class                                  doubleClass;

  private HashMap<Symbol, som.vmobjects.Object> globals = new HashMap<Symbol, som.vmobjects.Object>();
  private java.lang.String[]                    classPath;
  private boolean                               printAST;

  public static final java.lang.String          pathSeparator;
  public static final java.lang.String          fileSeparator;
  
  private final TruffleRuntime                  truffleRuntime;
  
  private final SymbolTable                     symbolTable;

  // TODO: this is not how it is supposed to be... it is just a hack to cope
  //       with the use of system.exit in SOM to enable testing
  private boolean                               avoidExit;
  private int                                   lastExitCode;
}
