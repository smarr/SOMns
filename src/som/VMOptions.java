package som;

import java.util.Arrays;


public class VMOptions {
  public static final String STANDARD_PLATFORM_FILE = "core-lib/Platform.som";
  public static final String STANDARD_KERNEL_FILE   = "core-lib/Kernel.som";

  public String   platformFile = STANDARD_PLATFORM_FILE;
  public String   kernelFile   = STANDARD_KERNEL_FILE;
  public final String[] args;
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
        return arguments;
      } else {
        if (arguments[currentArg].equals("--platform")) {
          platformFile = arguments[currentArg + 1];
          currentArg += 2;
        } else if (arguments[currentArg].equals("--kernel")) {
          kernelFile = arguments[currentArg + 1];
          currentArg += 2;
        } else {
          parsedArgument = false;
        }
      }
    }

    // store remaining arguments
    if (currentArg < arguments.length) {
      return Arrays.copyOfRange(arguments, currentArg, arguments.length);
    } else {
      return arguments;
    }
  }
}
