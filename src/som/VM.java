package som;

import java.util.Arrays;

import som.vm.Bootstrap;


public final class VM {

  private final boolean avoidExitForTesting;

  public VM(final boolean avoidExitForTesting) {
    this.avoidExitForTesting = avoidExitForTesting;
  }

  public VM() {
    this(false);
  }

  public static final String standardPlatformFile = "core-lib/Platform.som";

  public static class Options {
    public String   platformFile = standardPlatformFile;
    public String   appFile;
    public String[] args;
  }

  public Options processArguments(final String[] arguments) {
    Options result = new Options();

    int currentArg = 0;

    // parse optional --platform
    if (currentArg >= arguments.length) {
      printUsageAndExit();
    } else {
      if (arguments[currentArg].equals("--platform")) {
        result.platformFile = arguments[currentArg + 1];
        currentArg += 2;
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
    System.out.println("Usage: ./som.sh [--platform file-name] app-file [args...]");
    System.out.println("");
    System.out.println("  --platform file-name   SOM Platform module to be loaded");
    System.out.println("                         file-name defaults to 'core-lib/Platform.som'");
    System.out.println("");
    System.out.println("  app-file               file-name of the application to be executed");
    System.out.println("  args...                arguments passed to the application");
    // Checkstyle: resume

    if (!avoidExitForTesting) {
      System.exit(1);
    }
  }

  public long execute(final Options options) {
    Bootstrap.loadPlatformModule(options.platformFile);
    Bootstrap.initializeObjectSystem();
    return Bootstrap.executeApplication(options.appFile, options.args);
  }

  public static void main(final String[] args) {
    VM vm = new VM();
    Options options = vm.processArguments(args);
    System.exit((int) vm.execute(options));
  }
}
