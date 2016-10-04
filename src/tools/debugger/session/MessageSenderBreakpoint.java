package tools.debugger.session;

import tools.SourceCoordinate.FullSourceCoordinate;
import tools.debugger.FrontendConnector;


public class MessageSenderBreakpoint extends SectionBreakpoint {
  public MessageSenderBreakpoint(final boolean enabled, final FullSourceCoordinate coord) {
    super(enabled, coord);
  }

  /**
   * Note: Meant for use by serialization.
   */
  protected MessageSenderBreakpoint() {
    super();
  }

  @Override
  public void registerOrUpdate(final FrontendConnector frontend) {
    frontend.registerOrUpdate(this);
  }
}
