package som;

import java.util.Arrays;

import som.interpreter.TruffleCompiler;
import som.interpreter.actors.Actor;
import som.interpreter.actors.SFarReference;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.vm.Bootstrap;
import som.vmobjects.SObjectWithoutFields;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;


public final class VM {

  @CompilationFinal private static VM vm;
  private final boolean avoidExitForTesting;
  private int lastExitCode = 0;
  private volatile boolean shouldExit = false;
  private Options options;
  private boolean usesActors;
  private Thread mainThread;

  public static boolean DebugMode = false;

  public VM(final boolean avoidExitForTesting) {
    this.avoidExitForTesting = avoidExitForTesting;
    vm = this;
  }

  public VM() {
    this(false);
  }

  public static final String standardPlatformFile = "core-lib/Platform.som";
  public static final String standardKernelFile   = "core-lib/Kernel.som";

  public static class Options {
    public String   platformFile = standardPlatformFile;
    public String   kernelFile   = standardKernelFile;
    public String[] args;
  }

  public static boolean shouldExit() {
    return vm.shouldExit;
  }

  public int lastExitCode() {
    return lastExitCode;
  }

  public static boolean isUsingActors() {
    return vm.usesActors;
  }

  public static void hasSendMessages() {
    vm.usesActors = true;
  }

  public static String[] getArguments() {
    return vm.options.args;
  }

  public static void exit(final int errorCode) {
    vm.exitVM(errorCode);
  }

  private void exitVM(final int errorCode) {
    TruffleCompiler.transferToInterpreter("exit");
    // Exit from the Java system
    if (!avoidExitForTesting) {
      System.exit(errorCode);
    } else {
      lastExitCode = errorCode;
      shouldExit = true;
    }
  }

  public static void errorExit(final String message) {
    TruffleCompiler.transferToInterpreter("errorExit");
    errorPrintln("Runtime Error: " + message);
    exit(1);
  }

  public Options processVmArguments(final String[] arguments) {
    vm.options = new Options();

    int currentArg = 0;

    // parse optional --platform and --kernel, need to be the first arguments
    boolean parsedArgument = true;

    while (parsedArgument) {
      if (currentArg >= arguments.length) {
        return vm.options;
      } else {
        if (arguments[currentArg].equals("--platform")) {
          vm.options.platformFile = arguments[currentArg + 1];
          currentArg += 2;
        } else if (arguments[currentArg].equals("--kernel")) {
          vm.options.kernelFile = arguments[currentArg + 1];
          currentArg += 2;
        } else {
          parsedArgument = false;
        }
      }
    }

    // store remaining arguments
    if (currentArg < arguments.length) {
      vm.options.args = Arrays.copyOfRange(arguments, currentArg, arguments.length);
    }
    return vm.options;
  }

  protected void printUsageAndExit() {
    // Checkstyle: stop
    System.out.println("VM arguments, need to come before any application arguments:");
    System.out.println("");
    System.out.println("  --platform file-name   SOM Platform module to be loaded");
    System.out.println("                         file-name defaults to '" + standardPlatformFile + "'");
    System.out.println("  --kernel file-name     SOM Kernel module to be loaded");
    System.out.println("                         file-name defaults to '" + standardKernelFile + "'");
    // Checkstyle: resume

    if (!avoidExitForTesting) {
      System.exit(1);
    }
  }

  @TruffleBoundary
  public static void errorPrint(final String msg) {
    // Checkstyle: stop
    System.err.print(msg);
    // Checkstyle: resume
  }

  @TruffleBoundary
  public static void errorPrintln(final String msg) {
    // Checkstyle: stop
    System.err.println(msg);
    // Checkstyle: resume
  }

  @TruffleBoundary
  public static void errorPrintln() {
    // Checkstyle: stop
    System.err.println();
    // Checkstyle: resume
  }

  @TruffleBoundary
  public static void print(final String msg) {
    // Checkstyle: stop
    System.out.print(msg);
    // Checkstyle: resume
  }

  @TruffleBoundary
  public static void println(final String msg) {
    // Checkstyle: stop
    System.out.println(msg);
    // Checkstyle: resume
  }

  public static boolean isAvoidingExit() {
    return vm.avoidExitForTesting;
  }

  public static void setMainThread(final Thread t) {
    vm.mainThread = t;
  }

  public static Thread getMainThread() {
    return vm.mainThread;
  }

  public long execute() {
    Bootstrap.loadPlatformAndKernelModule(vm.options.platformFile,
        vm.options.kernelFile);

    Actor mainActor = Actor.initializeActorSystem();
    SObjectWithoutFields vmMirror = Bootstrap.initializeObjectSystem();
    Bootstrap.executeApplication(vmMirror, mainActor);
    return 0;
  }

  public static void main(final String[] args) {
    new VM();
    vm.processVmArguments(args);
    System.exit((int) vm.execute());
  }

  /** This is only meant to be used in unit tests. */
  public static void resetClassReferences(final boolean callFromUnitTest) {
    assert callFromUnitTest;
    SFarReference.setSOMClass(null);
    SPromise.setPairClass(null);
    SPromise.setSOMClass(null);
    SResolver.setSOMClass(null);
  }
}
