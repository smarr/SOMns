package tools.debugger.session;

import tools.SourceCoordinate.FullSourceCoordinate;


public abstract class SectionBreakpoint extends BreakpointInfo {
  protected final FullSourceCoordinate coord;

  public SectionBreakpoint(final boolean enabled, final FullSourceCoordinate coord) {
    super(enabled);
    this.coord = coord;
  }

  /**
   * Note: Meant for use by serialization.
   */
  protected SectionBreakpoint() {
    super();
    this.coord = null;
  }

  public FullSourceCoordinate getCoordinate() {
    return coord;
  }

  @Override
  public int hashCode() {
    return coord.hashCode();
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof SectionBreakpoint)) {
      return false;
    }
    SectionBreakpoint o = (SectionBreakpoint) obj;
    return coord.equals(o.coord);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + ": " + coord.toString();
  }
}
