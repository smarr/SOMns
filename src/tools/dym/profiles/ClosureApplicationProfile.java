package tools.dym.profiles;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.Invokable;

public class ClosureApplicationProfile extends Counter {

  private final Map<Invokable, Integer> callTargetMap;

  public ClosureApplicationProfile(final SourceSection source) {
    super(source);
    callTargetMap = new HashMap<>();
  }

  @TruffleBoundary
  public void recordInvocationTarget(final Invokable invokable) {
    callTargetMap.merge(invokable, 1, Integer::sum);
  }

  public Map<Invokable, Integer> getCallTargets() {
    return callTargetMap;
  }
}
