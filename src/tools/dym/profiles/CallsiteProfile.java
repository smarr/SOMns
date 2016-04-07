package tools.dym.profiles;

import java.util.HashMap;
import java.util.Map;

import som.interpreter.Invokable;
import som.vmobjects.SClass;

import com.oracle.truffle.api.source.SourceSection;


public class CallsiteProfile extends Counter {

  private final Map<Invokable, Integer> callTargetMap;
  private final Map<SClass, Integer>     receiverMap;

  public CallsiteProfile(final SourceSection source) {
    super(source);
    callTargetMap = new HashMap<>();
    receiverMap   = new HashMap<>();
  }

  public void recordReceiverType(final SClass rcvrClass) {
    receiverMap.merge(rcvrClass, 1, Integer::sum);
  }

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
