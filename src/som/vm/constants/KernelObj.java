package som.vm.constants;

import som.vmobjects.SObject;


// TODO: this is a lazyness hack, to avoid changing all places were SSymbols are
//       allocated, needs to be removed once we are sure that we don't need the
//       outer object for specific kernel classes like SSymbol
//       (all special subclasses of SAbstractObject)
public final class KernelObj {
  private KernelObj() { }

  public static final SObject kernel = new SObject(null, 23); // keep update to date with the actual number
}
