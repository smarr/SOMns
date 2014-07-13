package som.vm.constants;

import som.vmobjects.SDomain;
import som.vmobjects.SObject;


public final class Domain {
  public static final SObject standard;

  static {
    standard = SDomain.createStandardDomain();
  }
}
