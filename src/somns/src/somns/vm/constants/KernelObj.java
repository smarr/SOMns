package somns.vm.constants;

import somns.vmobjects.SObject.SImmutableObject;


public final class KernelObj {
  private KernelObj() {}

  public static final SImmutableObject kernel = new SImmutableObject(true, true);
}
