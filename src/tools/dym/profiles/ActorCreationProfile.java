package tools.dym.profiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.objectstorage.ClassFactory;
import tools.dym.profiles.ReadValueProfile.ProfileCounter;


public class ActorCreationProfile extends Counter implements CreateCounter {

  private final List<ProfileCounter> counters;

  public ActorCreationProfile(final SourceSection source) {
    super(source);
    counters = new ArrayList<>();
  }

  @Override
  public ProfileCounter createCounter(final ClassFactory type) {
    ProfileCounter counter = new ProfileCounter(type);
    counters.add(counter);
    return counter;
  }

  public Map<ClassFactory, Integer> getTypes() {
    return CreateCounter.getResults(counters);
  }
}
