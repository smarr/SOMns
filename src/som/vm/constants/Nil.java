package som.vm.constants;

import som.vmobjects.SObjectWithoutFields;


public final class Nil {
  public static final SObjectWithoutFields nilObject;

  static {
    nilObject = new SObjectWithoutFields();
  }
}
