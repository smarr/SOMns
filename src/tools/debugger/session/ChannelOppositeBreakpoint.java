package tools.debugger.session;

import tools.SourceCoordinate.FullSourceCoordinate;
import tools.debugger.FrontendConnector;


public class ChannelOppositeBreakpoint extends SectionBreakpoint {
  public ChannelOppositeBreakpoint(final boolean enabled, final FullSourceCoordinate coord) {
    super(enabled, coord);
  }

  public ChannelOppositeBreakpoint() {
    super();
  }

  @Override
  public void registerOrUpdate(final FrontendConnector frontend) {
    frontend.registerOrUpdate(this);
  }
}
