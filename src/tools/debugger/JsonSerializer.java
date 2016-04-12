package tools.debugger;

import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;

import som.interpreter.actors.SFarReference;
import som.vmobjects.SClass;

public final class JsonSerializer {

  private JsonSerializer() { }

  public static JSONObjectBuilder toJson(final String id, final SFarReference a) {
    Object value = a.getValue();
    assert value instanceof SClass;
    SClass actorClass = (SClass) value;

    JSONObjectBuilder builder = JSONHelper.object();
    builder.add("id", id);
    builder.add("name",     actorClass.getName().getString());
    builder.add("typeName", actorClass.getName().getString());

    return builder;
  }

}
