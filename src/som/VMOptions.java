package som;

import java.util.Arrays;


public class VMOptions {
  public static final String STANDARD_PLATFORM_FILE = "core-lib/Platform.som";
  public static final String STANDARD_KERNEL_FILE   = "core-lib/Kernel.som";

  public String   platformFile = STANDARD_PLATFORM_FILE;
  public String   kernelFile   = STANDARD_KERNEL_FILE;
  public final String[] args;
  public boolean  enableInstrumentation;

  public final boolean showUsage;

  public VMOptions(final String[] args) {
    this.args = processVmArguments(args);
    showUsage = args.length == 0;
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
        } else if (arguments[currentArg].equals("--enable-instrumentation")) {
          enableInstrumentation = true;
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
    VM.println("  --platform file-name      SOM Platform module to be loaded");
    VM.println("                            file-name defaults to '" + VMOptions.STANDARD_PLATFORM_FILE + "'");
    VM.println("  --kernel file-name        SOM Kernel module to be loaded");
    VM.println("                            file-name defaults to '" + VMOptions.STANDARD_KERNEL_FILE + "'");
    VM.println("");
    VM.println("  --enable-instrumentation  Allows instrumentation of programs for instance for profiling");
    VM.println("                            Note: specific tools will enable this.");
    VM.exit(1);
  }
}
