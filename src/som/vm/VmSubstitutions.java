package som.vm;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(som.VM.class)
public final class VmSubstitutions  {

  @Substitute
  public static boolean isHotSpotVM() {
    return false;
  }

}
