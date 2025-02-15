package bd.basic;

/**
 * A <code>ProgramDefinitionError</code> indicates a problem in the user-language program that
 * will prevent normal execution.
 *
 * <p>This error is the root of the error hierarchy used by Black Diamonds.
 * Language implementations should support it and possibly adopt it as the root of their
 * hierarchy of errors, too.
 */
public class ProgramDefinitionError extends Exception {
  private static final long serialVersionUID = -6231143390567454403L;

  public ProgramDefinitionError(final String message) {
    super(message);
  }
}
