package som.vm;


public final class Minimal {
  public static void main(final String[] arguments) {
    Universe u = Universe.current();
    String[] args = u.handleArguments(arguments);
    u.interpret(args[0], args[1]);
  }
}
