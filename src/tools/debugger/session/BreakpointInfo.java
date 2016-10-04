package tools.debugger.session;

import com.oracle.truffle.api.debug.Breakpoint;

import tools.debugger.FrontendConnector;


public abstract class BreakpointInfo {
  private final boolean enabled;

  protected BreakpointInfo(final boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Note: Meant mostly for use by serialization.
   */
  protected BreakpointInfo() {
    this(false);
  }

  /**
   * This is only the original value, the run-time value is represented by a
   * separate {@link BreakpointEnabling} or {@link Breakpoint} instance.
   *
   * @return whether the breakpoint was originally enabled
   */
  public boolean isEnabled() {
    return enabled;
  }

  public abstract void registerOrUpdate(FrontendConnector frontend);
}
