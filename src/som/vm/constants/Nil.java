package som.vm.constants;

import som.vmobjects.SObjectWithClass.SObjectWithoutFields;


public final class Nil {
  private Nil() { }

  public static final SObjectWithoutFields nilObject;

  static {
    nilObject = new SObjectWithoutFields();
  }

  public static boolean valueIsNil(final Object value) {
    return value == Nil.nilObject;
  }

  public static boolean valueIsNotNil(final Object value) {
    return value != Nil.nilObject;
  }
}
