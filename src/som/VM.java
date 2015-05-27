package som;

import java.util.Arrays;

import som.interpreter.TruffleCompiler;
import som.vm.Bootstrap;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;


public final class VM {

  @CompilationFinal private static VM vm;
  private final boolean avoidExitForTesting;
  private int lastExitCode = 0;
  private Options options;

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
    public String   appFile;
    public String[] args;
  }

  public Options processArguments(final String[] arguments) {
    Options result = new Options();
  public int lastExitCode() {
    return lastExitCode;
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
    }
  }

  public static void errorExit(final String message) {
    TruffleCompiler.transferToInterpreter("errorExit");
    errorPrintln("Runtime Error: " + message);
    exit(1);
  }


    int currentArg = 0;

    // parse optional --platform and --kernel
    boolean parsedArgument = true;

    while (parsedArgument) {
      if (currentArg >= arguments.length) {
        printUsageAndExit();
        return result;
      } else {
        if (arguments[currentArg].equals("--platform")) {
          result.platformFile = arguments[currentArg + 1];
          currentArg += 2;
        } else if (arguments[currentArg].equals("--kernel")) {
          result.kernelFile = arguments[currentArg + 1];
          currentArg += 2;
        } else {
          parsedArgument = false;
        }
      }
    }

    // parse app-file
    if (currentArg >= arguments.length) {
      printUsageAndExit();
    } else {
      result.appFile = arguments[currentArg];
      currentArg++;
    }

    // take args
    if (currentArg < arguments.length) {
      result.args = Arrays.copyOfRange(arguments, currentArg, arguments.length);
    }

    return result;
  }

  private void printUsageAndExit() {
    // Checkstyle: stop
    System.out.println("Usage: ./som.sh [--platform file-name] [--kernel file-name] app-file [args...]");
    System.out.println("");
    System.out.println("  --platform file-name   SOM Platform module to be loaded");
    System.out.println("                         file-name defaults to '" + standardPlatformFile + "'");
    System.out.println("  --kernel file-name     SOM Kernel module to be loaded");
    System.out.println("                         file-name defaults to '" + standardKernelFile + "'");
    System.out.println("");
    System.out.println("  app-file               file-name of the application to be executed");
    System.out.println("  args...                arguments passed to the application");
    // Checkstyle: resume

    if (!avoidExitForTesting) {
      System.exit(1);
    }
  }

  public long execute(final Options options) {
    Bootstrap.loadPlatformAndKernelModule(options.platformFile, options.kernelFile);
    Bootstrap.initializeObjectSystem();
    return Bootstrap.executeApplication(options.appFile, options.args);
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
  }

  public static void main(final String[] args) {
    VM vm = new VM();
    Options options = vm.processArguments(args);
    System.exit((int) vm.execute(options));
  }
}
