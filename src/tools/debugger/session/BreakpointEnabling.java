package tools.debugger.session;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;

/**
 * BreakpointEnabling represents the interpreter data for optimized testing
 * whether a breakpoint is active or not (enabled/disabled).
 *
 * This class is used for breakpoints that are not managed by the Truffle
 * framework directly.
 */
public class BreakpointEnabling<T extends BreakpointInfo> {
  private boolean   enabled;
  private transient Assumption unchanged;

  private T breakpointInfo;

  BreakpointEnabling(final T breakpointInfo) {
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
   * TODO: redundant, just a work around for the DSL, which has an issue with ! currently.
   */
  public boolean isDisabled() {
    return !isEnabled();
  }

  public Assumption getAssumption() {
    return unchanged;
  }
}
