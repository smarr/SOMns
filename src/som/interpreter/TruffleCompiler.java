package som.interpreter;

import com.oracle.truffle.api.CompilerDirectives;


public final class TruffleCompiler {
  public static void transferToInterpreter(final String reason) {
    CompilerDirectives.transferToInterpreter();
  }

  public static void transferToInterpreterAndInvalidate(final String reason) {
    CompilerDirectives.transferToInterpreterAndInvalidate();
  }
}
