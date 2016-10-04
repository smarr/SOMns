package tools.debugger.message;

import tools.debugger.session.Breakpoints.BreakpointId;


public class InitialBreakpointsResponds extends Respond {
  private final BreakpointId[] breakpoints;

  public InitialBreakpointsResponds(final BreakpointId[] breakpoints) {
    this.breakpoints = breakpoints;
  }

  public BreakpointId[] getBreakpoints() {
    return breakpoints;
  }
}
