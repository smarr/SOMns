package dym.profiles;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.source.SourceSection;


public final class OperationProfile extends Counter {

  private final Deque<Object[]> argumentsForExecutions;
  protected final int numSubexpressions;
  protected final Map<Arguments, Integer> argumentTypes;

  public OperationProfile(final SourceSection source, final int numSubexpressions) {
    super(source);
    this.numSubexpressions = numSubexpressions;
    argumentsForExecutions = new ArrayDeque<>();
    argumentTypes = new HashMap<>();
  }

  protected void recordArguments(final Object[] args) {
    argumentTypes.merge(
        new Arguments(args), 1, Integer::sum);
  }

  public void enterMainNode() {
    argumentsForExecutions.push(new Object[numSubexpressions]);
  }

  public void profileArgument(final int argIdx, final Object value) {
    // because of the self-optimizing nature of Truffle, we might get to
    // profile a node only after it and all it subnodes actually completed
    // executing, because we determine specializations based on actual arguments
    // so, the final results might be off by one, but this should not be
    // critical
    // Example: the `+` is problematic in `def length: 1 + self.length()`
    if (!argumentsForExecutions.isEmpty()) {
      argumentsForExecutions.peek()[argIdx] = value;
    }
  }

  public void profileReturn(final Object returnValue) {
    // because of the self-optimizing nature of Truffle, we might get to
    // profile a node only after it and all it subnodes actually completed
    // executing, because we determine specializations based on actual arguments
    // so, the final results might be off by one, but this should not be
    // critical
    // Example: the `+` is problematic in `def length: 1 + self.length()`
    if (!argumentsForExecutions.isEmpty()) {
      Object[] arguments = argumentsForExecutions.pop();
      arguments[0] = returnValue;
      recordArguments(arguments);
    }
  }
}
