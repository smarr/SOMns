package dym.profiles;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.source.SourceSection;


public abstract class OperationProfile extends Counter {

  protected int numKnownSubexpressions;
  protected final int numSubexpressions;
  protected final Map<Arguments, Integer> argumentTypes;

  public OperationProfile(final SourceSection source, final int numSubexpressions) {
    super(source);
    this.numSubexpressions = numSubexpressions;
    numKnownSubexpressions = 1; // the return value is stored in 0
    argumentTypes = new HashMap<>();
  }

  protected void recordArguments(final Object[] args) {
    argumentTypes.merge(
        new Arguments(args), 1, Integer::sum);
  }

  public int registerSubexpressionAndGetIdx() {
    numKnownSubexpressions += 1;
    return numKnownSubexpressions - 1;
  }

  public abstract void enterMainNode();

  public abstract void profileArgument(int argIdx, Object value);

  public abstract void profileReturn(Object returnValue);
}
