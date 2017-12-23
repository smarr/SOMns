package tools.superinstructions;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.source.SourceSection;


/**
 * A counter which keeps track of the number of node activations
 * and their respective result types (that is, Java types).
 */
final class TypeCounter {
  protected final SourceSection     source;
  private final Map<Class<?>, Long> activations;

  TypeCounter(final SourceSection source) {
    this.source = source;
    this.activations = new HashMap<>();
  }

  public SourceSection getSourceSection() {
    return source;
  }

  public void recordType(final Object result) {
    Class<?> type = result.getClass();
    activations.merge(type, 1L, Long::sum);
  }

  @Override
  public String toString() {
    return "TypeCnt[" + activations.size() + " types]";
  }

  public Map<Class<?>, Long> getActivations() {
    return activations;
  }
}
