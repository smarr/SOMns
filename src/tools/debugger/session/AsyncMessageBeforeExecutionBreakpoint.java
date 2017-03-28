package tools.debugger.session;

import tools.SourceCoordinate.FullSourceCoordinate;
import tools.debugger.FrontendConnector;


/**
 * Breakpoint on the RootTag node of a method, to halt before its execution, if the method was activated
 * asynchronously.
 *
 * <p>The method is identified by the source section info of the breakpoint.
 */
public class AsyncMessageBeforeExecutionBreakpoint extends SectionBreakpoint {
  public AsyncMessageBeforeExecutionBreakpoint(final boolean enabled, final FullSourceCoordinate coord) {
    super(enabled, coord);
  }

  /**
   * Note: Meant for use by serialization.
   */
  protected AsyncMessageBeforeExecutionBreakpoint() {
    super();
  }

  @Override
  public void registerOrUpdate(final FrontendConnector frontend) {
    frontend.registerOrUpdate(this);
  }
}
