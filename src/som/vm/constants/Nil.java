package som.vm.constants;

import som.vmobjects.SObject;


public final class Nil {
  public static final SObject nilObject;

  static {
    nilObject = SObject.create(0);
  }
}
