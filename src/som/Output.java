package som;

import java.io.PrintStream;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.vm.VmSettings;


/**
 * Provides methods that can be used to print messages, warnings, and
 * errors to standard out.
 *
 * Messages are displayed in the default terminal colors, warnings in
 * ANSI yellow and errors in ANSI red. For other colors, the
 * {@link #print(String, String)} and {@link #println(String, String)}
 * methods can be invoked with a message and color argument. Some viable
 * color arguments are provided by the {@link AnsiColor8} class.
 *
 */
public class Output {

  public static class AnsiColor8 {
    public static final String BLACK   = "\u001b[30m";
    public static final String RED     = "\u001b[31m";
    public static final String GREEN   = "\u001b[32m";
    public static final String YELLOW  = "\u001b[33m";
    public static final String BLUE    = "\u001b[34m";
    public static final String MAGENTA = "\u001b[35m";
    public static final String CYAN    = "\u001b[36m";
    public static final String WHITE   = "\u001b[37m";
    public static final String RESET   = "\u001b[0m";
  }

  @TruffleBoundary
  private static void print(final String msg, final String color, final PrintStream s) {
    if (VmSettings.ANSI_COLOR_IN_OUTPUT) {
      s.print(color + msg);
    } else {
      s.print(msg);
    }
  }

  @TruffleBoundary
  private static void println(final String msg, final String color, final PrintStream s) {
    if (VmSettings.ANSI_COLOR_IN_OUTPUT) {
      s.println(color + msg);
    } else {
      s.println(msg);
    }
  }

  public static void println(final String msg, final String color) {
    println(msg, color, System.out);
  }

  public static void print(final String msg) {
    print(msg, AnsiColor8.RESET, System.out);
  }

  public static void println(final String msg) {
    println(msg, AnsiColor8.RESET);
  }

  public static void errorPrintln(final String msg) {
    println(msg, AnsiColor8.RED, System.err);
  }

  public static void errorPrint(final String msg) {
    print(msg, AnsiColor8.RED, System.err);
  }

  public static void warningPrintln(final String msg) {
    println(msg, AnsiColor8.YELLOW, System.out);
  }

  public static void warningPrint(final String msg) {
    print(msg, AnsiColor8.YELLOW, System.out);
  }

  /**
   * This method is used to print reports about the number of created artifacts.
   * For example actors, messages and promises.
   */
  public static void printConcurrencyEntitiesReport(final String msg) {
    print(msg);
  }
}
