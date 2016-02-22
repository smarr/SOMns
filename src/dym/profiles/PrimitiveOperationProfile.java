package dym.profiles;

import java.util.Arrays;

import com.oracle.truffle.api.source.SourceSection;


/**
 * This is for primitive operations only, the cannot be recursive, so, we do
 * the simples possible thing.
 */
public final class PrimitiveOperationProfile extends OperationProfile {

  private final Object[] argumentsForCurrentExecution;

  public PrimitiveOperationProfile(final SourceSection source,
      final int numSubexpressions) {
    super(source, numSubexpressions);
    argumentsForCurrentExecution = new Object[numSubexpressions];
  }

  @Override
  public void enterMainNode() { }

  @Override
  public void profileArgument(final int argIdx, final Object value) {
    argumentsForCurrentExecution[argIdx] = value;
  }

  private boolean isDataComplete() {
    return numKnownSubexpressions == argumentsForCurrentExecution.length;
  }

  @Override
  public void profileReturn(final Object returnValue) {
    // because of the self-optimizing nature of Truffle, we might get to
    // profile a node only after it and all it subnodes actually completed
    // executing, because we determine specializations based on actual arguments
    // so, the final results might be off by one, but this should not be
    // critical
    if (isDataComplete()) {
      argumentsForCurrentExecution[0] = returnValue;
      recordArguments(argumentsForCurrentExecution);
    }
    Arrays.fill(argumentsForCurrentExecution, null);
  }
}
