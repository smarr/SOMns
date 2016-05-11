package tools.dym.profiles;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.objectstorage.ClassFactory;


public class ReadValueProfile extends Counter {

  private final Map<ClassFactory, Integer> typesOfReadValue;

  // TODO: add support for reading fields from profiled type of receiver objects.
  // need subexpression support for that

  public ReadValueProfile(final SourceSection source) {
    super(source);
    typesOfReadValue = new HashMap<>();
  }

  public void profileValueType(final ClassFactory valueType) {
    typesOfReadValue.merge(valueType, 1, Integer::sum);
  }

  public Map<ClassFactory, Integer> getTypeProfile() {
    return typesOfReadValue;
  }
}
