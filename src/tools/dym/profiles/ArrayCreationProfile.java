package tools.dym.profiles;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;


public class ArrayCreationProfile extends Counter {

  private final Map<Integer, Integer> arraySizes;

  public ArrayCreationProfile(final SourceSection source) {
    super(source);
    arraySizes = new HashMap<>();
  }

  @TruffleBoundary
  public void profileArraySize(final int size) {
    arraySizes.merge(size, 1, Integer::sum);
  }

  public Map<Integer, Integer> getSizes() {
    return arraySizes;
  }
}
