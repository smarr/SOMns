package tools.debugger.session;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;

import tools.debugger.entities.SteppingType;

/**
 * BreakpointEnabling represents the interpreter data for optimized testing
 * whether a breakpoint is active or a stepping target has been reached.
 *
 * <p>This class is used for breakpoints and stepping targets that are not
 * managed by the Truffle framework.
 */
public final class BreakpointEnabling {
  private boolean   enabled;
  private transient Assumption unchanged;

  private SectionBreakpoint breakpointInfo;

  BreakpointEnabling(final SectionBreakpoint breakpointInfo) {
    this.unchanged = Truffle.getRuntime().createAssumption("unchanged breakpoint");
    this.enabled = breakpointInfo.isEnabled();
    this.breakpointInfo = breakpointInfo;
  }

  public synchronized void setEnabled(final boolean enabled) {
    if (this.enabled != enabled) {
      this.enabled = enabled;
      this.unchanged.invalidate();
      this.unchanged = Truffle.getRuntime().createAssumption("unchanged breakpoint");
    }
  }

  public boolean isEnabled() {
    return unchanged.isValid() && enabled;
  }

  /**
   * Only use when checking the assumption before.
   */
  public boolean guardedEnabled() {
    return enabled;
  }

  public SteppingType getSteppingType() {
    return breakpointInfo.bpType.steppingType;
  }

  public Assumption getAssumption() {
    return unchanged;
  }
}
