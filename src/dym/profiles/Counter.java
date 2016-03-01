package dym.profiles;

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;
import com.oracle.truffle.api.utilities.JSONHelper.JSONStringBuilder;

import dym.JsonSerializable;

public class Counter implements JsonSerializable {
  protected final SourceSection source;
  private int invocationCount;

  public Counter(final SourceSection source) {
    this.source = source;
  }

  public SourceSection getSourceSection() {
    return source;
  }

  public void inc() {
    invocationCount += 1;
  }

  public int getValue() {
    return invocationCount;
  }

  @Override
  public String toString() {
    return "Cnt[" + invocationCount + ", " + source.getIdentifier() + "]";
  }

  @Override
  public JSONStringBuilder toJson() {
    JSONObjectBuilder result = JSONHelper.object();
    result.add("count", invocationCount);
    return result;
  }
}
