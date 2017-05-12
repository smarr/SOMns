package tools.debugger.nodes;

/**
 * Disabled breakpoint node that returns false all the time.
 * (use mainly when the Truffle debugger is not enabled)
 */
public final class DisabledBreakpointNode extends AbstractBreakpointNode {
  @Override
  public boolean executeShouldHalt() {
    return false;
  }
}
