package tools.dym.profiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.Invokable;
import som.interpreter.objectstorage.ClassFactory;
import tools.dym.nodes.TypeProfileNode;
import tools.dym.profiles.ReadValueProfile.ProfileCounter;


public class CallsiteProfile extends Counter implements CreateCounter {

  private final Map<Invokable, Counter>    callTargetMap;
  private final Map<ClassFactory, Integer> receiverMap;
  private final List<ProfileCounter>       counters;

  @SuppressWarnings("unused") private TypeProfileNode typeProfile;

  public CallsiteProfile(final SourceSection source) {
    super(source);
    callTargetMap = new HashMap<>();
    receiverMap = new HashMap<>();
    counters = new ArrayList<>();
  }

  @Override
  public ProfileCounter createCounter(final ClassFactory type) {
    ProfileCounter counter = new ProfileCounter(type);
    counters.add(counter);
    return counter;
  }

  public void setReceiverProfile(final TypeProfileNode rcvrProfile) {
    this.typeProfile = rcvrProfile;
  }

  public Counter createCounter(final Invokable invokable) {
    Counter c = callTargetMap.get(invokable);
    if (c != null) {
      return c;
    }
    c = new Counter();
    callTargetMap.put(invokable, c);
    return c;
  }

  public Map<Invokable, Integer> getCallTargets() {
    HashMap<Invokable, Integer> result = new HashMap<>();
    for (Entry<Invokable, Counter> e : callTargetMap.entrySet()) {
      result.put(e.getKey(), e.getValue().val);
    }
    return result;
  }

  public Map<ClassFactory, Integer> getReceivers() {
    Map<ClassFactory, Integer> result = new HashMap<>(receiverMap);
    for (ProfileCounter c : counters) {
      Integer val = result.get(c.getType());
      if (val == null) {
        result.put(c.getType(), c.getValue());
      } else {
        result.put(c.getType(), c.getValue() + val);
      }
    }
    return result;
  }

  public static final class Counter {
    private int val;

    public void inc() {
      val += 1;
    }
  }
}
