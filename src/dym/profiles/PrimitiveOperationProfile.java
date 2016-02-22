package dym.profiles;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.source.SourceSection;


/**
 * This is for primitive operations only, the cannot be recursive, so, we do
 * the simples possible thing.
 */
public class PrimitiveOperationProfile extends Counter {

  private int numKnownSubexpressions;

  // TODO: do in a language independent way
  private final Object[] argumentsForCurrentExecution;
  private final Map<Arguments, Integer> argumentTypes;

  public PrimitiveOperationProfile(final SourceSection source,
      final int numSubexpressions) {
    super(source);
    argumentsForCurrentExecution = new Object[numSubexpressions];
    argumentTypes = new HashMap<>();
    numKnownSubexpressions = 1; // the return value is stored in 0
  }

  public int registerSubexpressionAndGetIdx() {
    numKnownSubexpressions += 1;
    return numKnownSubexpressions - 1;
  }

  public void profileArgument(final int argIdx, final Object value) {
    argumentsForCurrentExecution[argIdx] = value;
  }

  public void profileReturn(final Object returnValue) {
    argumentsForCurrentExecution[0] = returnValue;
    argumentTypes.merge(
        new Arguments(argumentsForCurrentExecution), 1, Integer::sum);
    Arrays.fill(argumentsForCurrentExecution, null);
  }
}
