package dym.profiles;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.source.SourceSection;


public class ArrayCreationProfile extends Counter {

  private final Map<Long, Long> arraySizes;

  public ArrayCreationProfile(final SourceSection source) {
    super(source);
    arraySizes = new HashMap<>();
  }

  public void profileArraySize(final long size) {
    arraySizes.merge(size, 1L, Long::sum);
  }

  public Map<Long, Long> getSizes() {
    return arraySizes;
  }
}
