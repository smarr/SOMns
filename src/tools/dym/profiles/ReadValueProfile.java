package tools.dym.profiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.objectstorage.ClassFactory;


public class ReadValueProfile extends Counter implements CreateCounter {

  private final List<ProfileCounter> counters;

  // TODO: add support for reading fields from profiled type of receiver objects.
  // need subexpression support for that

  public ReadValueProfile(final SourceSection source) {
    super(source);
    counters = new ArrayList<>();
  }

  public Map<ClassFactory, Integer> getTypeProfile() {
    return CreateCounter.getResults(counters);
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
