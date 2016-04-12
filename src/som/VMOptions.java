package som;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;


public class VMOptions {
  public static final String STANDARD_PLATFORM_FILE = "core-lib/Platform.som";
  public static final String STANDARD_KERNEL_FILE   = "core-lib/Kernel.som";

  public String   platformFile = STANDARD_PLATFORM_FILE;
  public String   kernelFile   = STANDARD_KERNEL_FILE;
  public final String[] args;
  public final boolean showUsage;

  @CompilationFinal public boolean debuggerEnabled;
  @CompilationFinal public boolean webDebuggerEnabled;
  @CompilationFinal public boolean profilingEnabled;
  @CompilationFinal public boolean dynamicMetricsEnabled;
  @CompilationFinal public boolean highlightingEnabled;

  public VMOptions(final String[] args) {
    this.args = processVmArguments(args);
    showUsage = args.length == 0;
    if (!VmSettings.INSTRUMENTATION &&
        (debuggerEnabled || webDebuggerEnabled || profilingEnabled ||
        dynamicMetricsEnabled || highlightingEnabled)) {
      throw new IllegalStateException(
          "Instrumentation is not enabled, but one of the tools is used. " +
          "Please set -D" + VmSettings.INSTRUMENTATION_PROP + "=true");
    }
  }

  private String[] processVmArguments(final String[] arguments) {
    int currentArg = 0;

    // parse optional --platform and --kernel, need to be the first arguments
    boolean parsedArgument = true;

    while (parsedArgument) {
      if (currentArg >= arguments.length) {
        return null;
      } else {
        if (arguments[currentArg].equals("--platform")) {
          platformFile = arguments[currentArg + 1];
          currentArg += 2;
        } else if (arguments[currentArg].equals("--kernel")) {
          kernelFile = arguments[currentArg + 1];
          currentArg += 2;
        } else if (arguments[currentArg].equals("--debug")) {
          debuggerEnabled = true;
          currentArg += 1;
        } else if (arguments[currentArg].equals("--web-debug")) {
          webDebuggerEnabled = true;
          currentArg += 1;
        } else if (arguments[currentArg].equals("--profile")) {
          profilingEnabled = true;
          currentArg += 1;
        } else if (arguments[currentArg].equals("--dynamic-metrics")) {
          dynamicMetricsEnabled = true;
          currentArg += 1;
        } else if (arguments[currentArg].equals("--highlight")) {
          highlightingEnabled = true;
          currentArg += 1;
        } else {
          parsedArgument = false;
        }
      }
    }

    // store remaining arguments
    if (currentArg < arguments.length) {
      return Arrays.copyOfRange(arguments, currentArg, arguments.length);
    } else {
      return null;
    }
  }

  public static void printUsageAndExit() {
    VM.println("VM arguments, need to come before any application arguments:");
    VM.println("");
    VM.println("  --platform file-name   SOM Platform module to be loaded");
    VM.println("                         file-name defaults to '" + VMOptions.STANDARD_PLATFORM_FILE + "'");
    VM.println("  --kernel file-name     SOM Kernel module to be loaded");
    VM.println("                         file-name defaults to '" + VMOptions.STANDARD_KERNEL_FILE + "'");
    VM.println("");
    VM.println("  --debug                Run in Truffle Debugger/REPL");
    VM.println("  --web-debug            Start web debugger");
    VM.println("");
    VM.println("  --profile              Enable the TruffleProfiler");
    VM.println("  --dynamic-metrics      Enable the DynamicMetrics tool");
    VM.println("  --highlight            Enable the Highlight tool"); // TODO: this should take a parameter at some point, but for that we need to be able to access config options from tools
    VM.exit(1);
  }
}
