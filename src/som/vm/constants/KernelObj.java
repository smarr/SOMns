package som.vm.constants;

import som.vmobjects.SObject.SImmutableObject;


// TODO: this was orignially a lazy hack to pass the enclosing object to Objects
//       but, actually, this is not needed. However, we still might need this object
//       to solve circular dependencies in the bootstrap
public final class KernelObj {
  private KernelObj() { }

  public static final SImmutableObject kernel = new SImmutableObject(true);
}
