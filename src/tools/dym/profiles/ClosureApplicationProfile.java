package tools.dym.profiles;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.Invokable;


public class ClosureApplicationProfile extends Counter {

  private final Map<Invokable, ActivationCounter> callTargetMap;

  public ClosureApplicationProfile(final SourceSection source) {
    super(source);
    callTargetMap = new HashMap<>();
  }

  // TODO: remove code duplication with CallsiteProfile

  public ActivationCounter createCounter(final Invokable invokable) {
    ActivationCounter c = callTargetMap.get(invokable);
    if (c != null) {
      return c;
    }
    c = new ActivationCounter();
    callTargetMap.put(invokable, c);
    return c;
  }

  public Map<Invokable, Integer> getCallTargets() {
    HashMap<Invokable, Integer> result = new HashMap<>();
    for (Entry<Invokable, ActivationCounter> e : callTargetMap.entrySet()) {
      result.put(e.getKey(), e.getValue().val);
    }
    return result;
  }

  public static final class ActivationCounter {
    private int val;

    public void inc() {
      val += 1;
    }
  }
}
