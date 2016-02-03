package dym.profiles;

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;
import com.oracle.truffle.api.utilities.JSONHelper.JSONStringBuilder;

import dym.JsonSerializable;


public class BranchProfile implements JsonSerializable {
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

  @Override
  public JSONStringBuilder toJson() {
    JSONObjectBuilder result = JSONHelper.object();
    result.add("trueCount",  trueCount);
    result.add("falseCount", falseCount);
    return result;
  }
}
