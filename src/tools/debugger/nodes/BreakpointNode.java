package tools.debugger.nodes;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

import tools.debugger.session.BreakpointEnabling;

/**
 * Node to represent a breakpoint at the AST level.
 * It has two possible states, enable or disable.
 */
public abstract class BreakpointNode extends AbstractBreakpointNode {
  protected final BreakpointEnabling<?> breakpoint;

  protected BreakpointNode(final BreakpointEnabling<?> breakpoint) {
    this.breakpoint = breakpoint;
  }

  @Specialization(assumptions = "bpUnchanged", guards = "breakpoint.isDisabled()")
  public final boolean breakpointDisabled(
      @Cached("breakpoint.getAssumption()") final Assumption bpUnchanged) {
    return false;
  }

  @Specialization(assumptions = {"bpUnchanged"}, guards = {"breakpoint.isEnabled()"})
  public final boolean breakpointEnabled(
      @Cached("breakpoint.getAssumption()") final Assumption bpUnchanged) {
    return true;
  }
}
