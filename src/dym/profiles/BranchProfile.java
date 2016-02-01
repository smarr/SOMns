package dym.profiles;

import com.oracle.truffle.api.source.SourceSection;


public class BranchProfile {
  private final SourceSection source;
  private long trueCount;
  private long falseCount;

  public BranchProfile(final SourceSection source) {
    this.source = source;
  }

  public void profile(final boolean branchValue) {
    if (branchValue) {
      trueCount += 1;
      assert trueCount > 0 : "TODO: handle overflow, by calculating a ratio";
    } else {
      falseCount += 1;
      assert falseCount > 0 : "TODO: handle overflow, by calculating a ratio";
    }
  }
}
