package som.compiler;

import com.oracle.truffle.api.source.SourceSection;

public abstract class ProgramDefinitionError extends Exception {
  private static final long serialVersionUID = 318305400750674461L;

  public ProgramDefinitionError(final String message) {
    super(message);
  }

  public abstract static class SemanticDefinitionError extends ProgramDefinitionError {
    private static final long serialVersionUID = -3374814429682547685L;
    private final SourceSection source;

    protected SemanticDefinitionError(final String message, final SourceSection source) {
      super(message);
      this.source = source;
    }

    public SourceSection getSourceSection() {
      return source;
    }

    @Override
    public String toString() {
      return source.getSource().getName() + ":" + source.getStartLine() + ":" +
            source.getStartColumn() + ":error: " + getMessage();
    }
  }
}
