package som.vm.constants;

import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SObject;



public final class Globals {
  public static final SObject trueObject;
  public static final SObject falseObject;
  public static final SObject systemObject;

  public static final SClass  trueClass;
  public static final SClass  falseClass;
  public static final SClass  systemClass;

 static {
    trueObject   = Universe.current().getTrueObject();
    falseObject  = Universe.current().getFalseObject();
    systemObject = Universe.current().getSystemObject();

    trueClass   = Universe.current().getTrueClass();
    falseClass  = Universe.current().getFalseClass();
    systemClass = Universe.current().getSystemClass();
  }
}
