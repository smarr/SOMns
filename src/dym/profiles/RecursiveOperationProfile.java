package dym.profiles;

import java.util.ArrayDeque;
import java.util.Deque;

import com.oracle.truffle.api.source.SourceSection;


public final class RecursiveOperationProfile extends OperationProfile {

  private final Deque<Object[]> argumentsForExecutions;

  public RecursiveOperationProfile(final SourceSection source, final int numSubexpressions) {
    super(source, numSubexpressions);
    argumentsForExecutions = new ArrayDeque<>();
  }

  @Override
  public void enterMainNode() {
    argumentsForExecutions.push(new Object[numSubexpressions]);
  }

  @Override
  public void profileArgument(final int argIdx, final Object value) {
    argumentsForExecutions.peek()[argIdx] = value;
  }

  @Override
  public void profileReturn(final Object returnValue) {
    // because of the self-optimizing nature of Truffle, we might get to
    // profile a node only after it and all it subnodes actually completed
    // executing, because we determine specializations based on actual arguments
    // so, the final results might be off by one, but this should not be
    // critical
    if (!argumentsForExecutions.isEmpty()) {
      Object[] arguments = argumentsForExecutions.pop();
      arguments[0] = returnValue;
      recordArguments(arguments);
    }
  }
}
