package tools.debugger.session;

import java.util.Objects;

import tools.SourceCoordinate.FullSourceCoordinate;
import tools.debugger.FrontendConnector;
import tools.debugger.entities.BreakpointType;


public final class SectionBreakpoint extends BreakpointInfo {
  protected final FullSourceCoordinate coord;
  protected final BreakpointType       bpType;

  public SectionBreakpoint(final boolean enabled,
      final FullSourceCoordinate coord, final BreakpointType type) {
    super(enabled);
    this.coord = coord;
    this.bpType = type;
  }

  public SectionBreakpoint(final FullSourceCoordinate coord,
      final BreakpointType type) {
    this(false, coord, type);
  }

  /**
   * Note: Meant for use by serialization.
   */
  protected SectionBreakpoint() {
    super();
    this.coord = null;
    this.bpType = null;
  }

  public FullSourceCoordinate getCoordinate() {
    return coord;
  }

  public BreakpointType getType() {
    return bpType;
  }

  @Override
  public void registerOrUpdate(final FrontendConnector frontend) {
    bpType.registerOrUpdate(frontend.getBreakpoints(), this);
  }

  @Override
  public int hashCode() {
    return Objects.hash(coord, bpType);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    SectionBreakpoint o = (SectionBreakpoint) obj;
    return coord.equals(o.coord) && o.bpType == bpType;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + ": " + coord.toString();
  }
}
