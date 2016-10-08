package tools.debugger.session;

import com.oracle.truffle.api.source.SourceSection;

import tools.SourceCoordinate;
import tools.SourceCoordinate.FullSourceCoordinate;
import tools.debugger.FrontendConnector;


public class MessageReceiveBreakpoint extends SectionBreakpoint {
  public MessageReceiveBreakpoint(final boolean enabled, final FullSourceCoordinate coord) {
    super(enabled, coord);
  }

  public MessageReceiveBreakpoint(final boolean enabled, final SourceSection section) {
    this(enabled, SourceCoordinate.create(section));
  }

  /**
   * Note: Meant for use by serialization.
   */
  protected MessageReceiveBreakpoint() {
    super();
  }

  @Override
  public void registerOrUpdate(final FrontendConnector frontend) {
    frontend.registerOrUpdate(this);
  }
}
