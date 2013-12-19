package som.interpreter;


// TODO: think, I don't actually need this one

/**
 * Represents the variables in the outer scope of a block method.
 * These variables are not located on the stack in order to make them
 * accessible without requiring materialized frames.
 *
 * @author Stefan Marr
 */
public class UpValues {
  private final UpValues outerContext;
  private final Object[] values;

  public UpValues(final UpValues outerContext, final Object[] values) {
    this.outerContext = outerContext;
    this.values       = values;
  }

  public UpValues getOuterContext() {
    return outerContext;
  }

  public Object[] getValues() {
    return values;
  }
}
