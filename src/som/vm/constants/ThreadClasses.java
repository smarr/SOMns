package som.vm.constants;

import som.vm.Universe;
import som.vmobjects.SClass;


public class ThreadClasses {
  public static final SClass conditionClass;
  public static final SClass delayClass;
  public static final SClass mutexClass;
  public static final SClass threadClass;

  static {
    conditionClass = Universe.newSystemClass();
    delayClass     = Universe.newSystemClass();
    mutexClass     = Universe.newSystemClass();
    threadClass    = Universe.newSystemClass();
  }
}
