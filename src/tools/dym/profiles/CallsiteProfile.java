package tools.dym.profiles;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.Invokable;
import som.vmobjects.SClass;


public class CallsiteProfile extends Counter {

  private final Map<Invokable, Integer> callTargetMap;
  private final Map<SClass, Integer>     receiverMap;

  public CallsiteProfile(final SourceSection source) {
    super(source);
    callTargetMap = new HashMap<>();
    receiverMap   = new HashMap<>();
  }

  @TruffleBoundary
  public void recordReceiverType(final SClass rcvrClass) {
    receiverMap.merge(rcvrClass, 1, Integer::sum);
  }

  @TruffleBoundary
  public void recordInvocationTarget(final Invokable invokable) {
    callTargetMap.merge(invokable, 1, Integer::sum);
  }

  public Map<Invokable, Integer> getCallTargets() {
    return callTargetMap;
  }

  public Map<SClass, Integer> getReceivers() {
    return receiverMap;
  }
}
