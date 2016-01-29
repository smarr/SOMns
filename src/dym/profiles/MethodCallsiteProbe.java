package dym.profiles;

import com.oracle.truffle.api.source.SourceSection;

public class MethodCallsiteProbe extends Counter {

  public MethodCallsiteProbe(final SourceSection source) {
    super(source);
  }
}
