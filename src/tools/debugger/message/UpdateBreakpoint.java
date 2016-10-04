package tools.debugger.message;

import tools.debugger.session.Breakpoints.BreakpointId;

public class UpdateBreakpoint extends Respond {
  private final BreakpointId breakpoint;

  public UpdateBreakpoint(final BreakpointId breakpoint) {
    this.breakpoint = breakpoint;
  }

  public BreakpointId getBreakpoint() {
    return breakpoint;
  }
}
