package som.vm;

import java.io.File;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import som.Output;


public class VmOptions {
  public static final String STANDARD_PLATFORM_FILE = "core-lib/Platform.ns";
  public static final String STANDARD_KERNEL_FILE   = "core-lib/Kernel.ns";

  public String         platformFile = STANDARD_PLATFORM_FILE;
  public String         kernelFile   = STANDARD_KERNEL_FILE;
  public final Object[] args;
  private final boolean showUsage;

  /**
   * Used in {@link som.tests.BasicInterpreterTests} to identify which basic test method to
   * invoke.
   */
  public final String testSelector;

  @CompilationFinal public boolean webDebuggerEnabled;
  @CompilationFinal public boolean profilingEnabled;
  @CompilationFinal public boolean siCandidateIdentifierEnabled;
  @CompilationFinal public boolean coverageEnabled;
  @CompilationFinal public String  coverageFile;

  public VmOptions(final String[] args) {
    this(args, null);
  }

  public VmOptions(final String[] args, final String testSelector) {
    this.testSelector = "".equals(testSelector) ? null : testSelector;
    this.args = processVmArguments(args);
    showUsage = args.length == 0;
    if (!VmSettings.INSTRUMENTATION &&
        (webDebuggerEnabled || profilingEnabled ||
            coverageEnabled || siCandidateIdentifierEnabled)) {
      throw new IllegalStateException(
          "Instrumentation is not enabled, but one of the tools is used. " +
              "Please set -D" + VmSettings.INSTRUMENTATION_PROP + "=true");
    }
  }

  public boolean isTestExecution() {
    return testSelector != null;
  }

  private Object[] processVmArguments(final String[] arguments) {
    int currentArg = 0;

    // parse optional --platform and --kernel, need to be the first arguments
    boolean parsedArgument = true;

    while (parsedArgument) {
      if (currentArg >= arguments.length) {
        return new String[0];
      } else {
        if (arguments[currentArg].equals("--platform")) {
          platformFile = arguments[currentArg + 1];
          currentArg += 2;
        } else if (arguments[currentArg].equals("--kernel")) {
          kernelFile = arguments[currentArg + 1];
          currentArg += 2;
        } else if (arguments[currentArg].equals("--web-debug")) {
          webDebuggerEnabled = true;
          currentArg += 1;
        } else if (arguments[currentArg].equals("--profile")) {
          profilingEnabled = true;
          currentArg += 1;
        } else if (arguments[currentArg].equals("--si-candidates")) {
          siCandidateIdentifierEnabled = true;
          currentArg += 1;
        } else if (arguments[currentArg].equals("--coverage")) {
          coverageEnabled = true;
          coverageFile = arguments[currentArg + 1];
          currentArg += 2;
        } else {
          parsedArgument = false;
        }
      }
    }

    // store remaining arguments
    if (currentArg < arguments.length) {
      return Arrays.copyOfRange(arguments, currentArg, arguments.length, Object[].class);
    } else {
      return new Object[0];
    }
  }

  public boolean isConfigUsable() {
    boolean noPlatformFile = !(new File(platformFile)).exists();
    if (noPlatformFile) {
      Output.errorPrintln("The platformFile " + platformFile
          + " was not found. Please check the --platform setting.");
    }
    boolean noKernelFile = !(new File(kernelFile)).exists();
    if (noKernelFile) {
      Output.errorPrintln("The kernelFile " + kernelFile
          + " was not found. Please check the --kernel setting.");
    }
    if (noPlatformFile || noKernelFile) {
      return false;
    }

    if (!showUsage) {
      return true;
    }

    Output.println("VM arguments, need to come before any application arguments:");
    Output.println("");
    Output.println("  --platform file-name   SOM Platform module to be loaded");
    Output.println("                         file-name defaults to '"
        + VmOptions.STANDARD_PLATFORM_FILE + "'");
    Output.println("  --kernel file-name     SOM Kernel module to be loaded");
    Output.println("                         file-name defaults to '"
        + VmOptions.STANDARD_KERNEL_FILE + "'");
    Output.println("");
    Output.println("  --debug                Run in Truffle Debugger/REPL");
    Output.println("  --web-debug            Start web debugger");
    Output.println("");
    Output.println("  --profile              Enable the TruffleProfiler");
    Output.println("  --si-candidates        Enable the Super-instruction candidate tool");
    Output.println(
        "  --coveralls REPO_TOKEN Enable the Coverage tool and reporting to Coveralls.io");

    return false;
  }
}
