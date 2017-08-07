package tools.dym.profiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.interpreter.objectstorage.ClassFactory;


public class ReadValueProfile extends Counter implements CreateCounter {

  private final Map<ClassFactory, Integer> typesOfReadValue;
  private final List<ProfileCounter>       counters;

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
    Map<ClassFactory, Integer> result = new HashMap<>(typesOfReadValue);
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

  @Override
  public ProfileCounter createCounter(final ClassFactory type) {
    ProfileCounter counter = new ProfileCounter(type);
    counters.add(counter);
    return counter;
  }

  public static final class ProfileCounter {
    private int                count;
    private final ClassFactory type;

    public ProfileCounter(final ClassFactory type) {
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
