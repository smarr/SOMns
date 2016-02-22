package dym.profiles;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.source.SourceSection;


public class LoopProfile extends Counter {

  private int currentIterations;
  private final Map<Integer, Integer> loopIterations;

  public LoopProfile(final SourceSection source) {
    super(source);
    loopIterations = new HashMap<>();
    currentIterations = 0;
  }

  public void recordLoopIteration() {
    currentIterations += 1;
    assert currentIterations >= 0 : "TODO: handle overflow";
  }

  public void recordLoopExit() {
    loopIterations.merge(currentIterations, 1, Integer::sum);
    currentIterations = 0;
  }
}
