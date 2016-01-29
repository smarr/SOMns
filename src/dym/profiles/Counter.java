package dym.profiles;

import com.oracle.truffle.api.source.SourceSection;

public class Counter {
  private final SourceSection source;
  private int invocationCount;

  public Counter(final SourceSection source) {
    this.source = source;
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
}
