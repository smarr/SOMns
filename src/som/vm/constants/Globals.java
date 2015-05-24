package som.vm.constants;

import som.vm.Universe;
import som.vmobjects.SObject;



public final class Globals {
  public static final SObject trueObject;
  public static final SObject falseObject;

 static {
    trueObject   = Universe.current().getTrueObject();
    falseObject  = Universe.current().getFalseObject();
  }
}
