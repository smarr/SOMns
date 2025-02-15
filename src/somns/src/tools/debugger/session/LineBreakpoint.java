package tools.debugger.session;

import java.net.URI;
import java.util.Objects;

import tools.debugger.FrontendConnector;


public class LineBreakpoint extends BreakpointInfo {
  private final URI sourceUri;
  private final int line;

  public LineBreakpoint(final boolean enabled, final URI sourceUri, final int line) {
    super(enabled);
    this.sourceUri = sourceUri;
    this.line = line;
  }

  /**
   * Note: Meant for use by serialization.
   */
  protected LineBreakpoint() {
    super();
    this.sourceUri = null;
    this.line = 0;
  }

  public int getLine() {
    return line;
  }

  public URI getURI() {
    return sourceUri;
  }

  @Override
  public void registerOrUpdate(final FrontendConnector frontend) {
    frontend.registerOrUpdate(this);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceUri, line);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    LineBreakpoint o = (LineBreakpoint) obj;
    return o.line == line && o.sourceUri.equals(sourceUri);
  }

  @Override
  public String toString() {
    return "LineBreakpoint[" + line + ", " + sourceUri.toString() + "]";
  }
}
