package tools.dym.profiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import som.interpreter.objectstorage.ClassFactory;
import tools.dym.profiles.ReadValueProfile.ProfileCounter;


public interface CreateCounter {
  ProfileCounter createCounter(ClassFactory factory);

  static Map<ClassFactory, Integer> getResults(final List<ProfileCounter> counters) {
    Map<ClassFactory, Integer> result = new HashMap<>();
    for (ProfileCounter c : counters) {
      Integer val = result.get(c.getType());
      if (val == null) {
        result.put(c.getType(), c.getValue());
      } else {
        // there may be multiple counters for the same type
        result.put(c.getType(), c.getValue() + val);
      }
    }
    return result;
  }
}
