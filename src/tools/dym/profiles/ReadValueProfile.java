package tools.dym.profiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.interpreter.objectstorage.ClassFactory;


public class ReadValueProfile extends Counter {

  private final Map<ClassFactory, Integer> typesOfReadValue;
  private final List<ProfileCounter> counters;

  // TODO: add support for reading fields from profiled type of receiver objects.
  // need subexpression support for that

  public ReadValueProfile(final SourceSection source) {
    super(source);
    typesOfReadValue = new HashMap<>();
    counters = new ArrayList<>();
  }

  public void profileValueType(final ClassFactory valueType) {
    VM.callerNeedsToBeOptimized("This is a fallback method");
    typesOfReadValue.merge(valueType, 1, Integer::sum);
  }

  public Map<ClassFactory, Integer> getTypeProfile() {
    for (ProfileCounter c : counters) {
      assert !typesOfReadValue.containsKey(c.getType()) : "need a smarter merge if we already got this type here";
      typesOfReadValue.put(c.getType(), c.getValue());
    }
    return typesOfReadValue;
  }

  public ProfileCounter createCounter(final ClassFactory type) {
    ProfileCounter counter = new ProfileCounter(type);
    counters.add(counter);
    return counter;
  }

  public static final class ProfileCounter {
    private int count;
    private final ClassFactory type;

    private ProfileCounter(final ClassFactory type) {
      this.type = type;
    }

    public void inc() {
      count += 1;
    }

    public ClassFactory getType() {
      return type;
    }

    public int getValue() {
      return count;
    }
  }
}
