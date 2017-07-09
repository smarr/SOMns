package tools.dym.superinstructions;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * This class represents an activation context consisting of a trace and a Java type.
 * Its objects are immutable and hashable.
 * Traces are represented as Object arrays.
 */
public class ActivationContext {
  private final Object[] trace;
  private final String   javaType;

  public ActivationContext(final Object[] trace, final String javaType) {
    this.trace = trace;
    this.javaType = javaType;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ActivationContext that = (ActivationContext) o;
    return Arrays.equals(trace, that.trace) &&
        Objects.equals(javaType, that.javaType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(Arrays.deepHashCode(trace), javaType);
  }

  /**
   * @return a String representation of the activation trace
   */
  public String getTraceAsString() {
    return Arrays.stream(trace).map(Object::toString).collect(Collectors.joining(","));
  }

  /**
   * @return a String representation of the activation context in
   *         the form of "C_0,s_0,...C_n[type]"
   */
  @Override
  public String toString() {
    return String.format("%s[%s]", getTraceAsString(), javaType);
  }

  public Object[] getTrace() {
    return trace;
  }

  public String getJavaType() {
    return javaType;
  }

  /**
   * Assuming a trace [C_0, s_0, ..., C_n], return C_n.
   */
  public String getLeafClass() {
    return getClass(getNumberOfClasses() - 1);
  }

  /**
   * Assuming a trace [C_0, s_0, ..., s_{n-1}, C_n], return s_{n-1}.
   */
  public int getLeafChildIndex() {
    return getChildIndex(getNumberOfClasses() - 2);
  }

  /**
   * Assuming a trace [C_0, s_0, ..., C_n], return n.
   */
  public int getNumberOfClasses() {
    return (trace.length + 1) / 2;
  }

  /**
   * Given i and assuming a trace [C_0, s_0, ..., C_n], return C_i.
   */
  public String getClass(final int i) {
    assert i < getNumberOfClasses();
    return (String) trace[i * 2];
  }

  /**
   * Given i and assuming a trace [C_0, s_0, ..., C_n], return s_i.
   */
  public int getChildIndex(final int i) {
    assert i < getNumberOfClasses() - 1;
    return (Integer) trace[i * 2 + 1];
  }

  /**
   * Given an object array, return true if it is a prefix of the activation trace.
   */
  public boolean traceStartsWith(final Object[] prefix) {
    if (trace.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (!trace[i].equals(prefix[i])) {
        return false;
      }
    }
    return true;
  }
}
