package som.vm.constants;

import som.vm.Universe;
import som.vmobjects.SClass;


public final class Blocks {
  public static final SClass blockClass1;
  public static final SClass blockClass2;
  public static final SClass blockClass3;

  static {
    blockClass1 = Universe.current().getBlockClass(1);
    blockClass2 = Universe.current().getBlockClass(2);
    blockClass3 = Universe.current().getBlockClass(3);
  }
}
