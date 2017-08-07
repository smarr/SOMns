package tools.debugger;

import tools.debugger.entities.SteppingType;


public final class SteppingStrategy {

  private final SteppingType type;
  private boolean            consumed;

  public SteppingStrategy(final SteppingType type) {
    this.type = type;
    this.consumed = false;
  }

  public boolean is(final SteppingType type) {
    if (!consumed && this.type == type) {
      consumed = true;
      return true;
    } else {
      return false;
    }
  }
}
