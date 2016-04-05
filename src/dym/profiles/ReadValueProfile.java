package dym.profiles;

import java.util.HashMap;
import java.util.Map;

import som.vmobjects.SClass;

import com.oracle.truffle.api.source.SourceSection;


public class ReadValueProfile extends Counter {

  private final Map<SClass, Integer> typesOfReadValue;

  // TODO: add support for reading fields from profiled type of receiver objects.
  // need subexpression support for that

  public ReadValueProfile(final SourceSection source) {
    super(source);
    typesOfReadValue = new HashMap<>();
  }

  public void profileValueType(final SClass valueType) {
    typesOfReadValue.merge(valueType, 1, Integer::sum);
  }

  public Map<SClass, Integer> getTypeProfile() {
    return typesOfReadValue;
  }
}
