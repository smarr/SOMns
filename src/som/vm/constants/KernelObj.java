package som.vm.constants;

import som.vmobjects.SObject.SImmutableObject;


public final class KernelObj {
  private KernelObj() { }
  public static final SImmutableObject kernel = new SImmutableObject(true, true);
}
