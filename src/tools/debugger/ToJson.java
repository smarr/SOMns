package tools.debugger;

import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;

import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SFarReference;
import som.vmobjects.SClass;


/**
 * Creates JSON representations for objects based on simple data.
 *
 * Note, any kind of data collection should be done for instance in
 * {@link JsonSerializer}.
 */
public final class ToJson {

  public static JSONObjectBuilder farReference(final String id, final SFarReference a) {
    Object value = a.getValue();
    assert value instanceof SClass;
    SClass actorClass = (SClass) value;

    JSONObjectBuilder builder = JSONHelper.object();
    builder.add("id", id);
    builder.add("name",     actorClass.getName().getString());
    builder.add("typeName", actorClass.getName().getString());

    return builder;
  }

  public static JSONObjectBuilder message(final String senderId,
      final String targetId, final int mId, final EventualMessage m) {
    JSONObjectBuilder jsonM = JSONHelper.object();
    jsonM.add("id", "m-" + mId);
    jsonM.add("sender",   senderId);
    jsonM.add("receiver", targetId);
    return jsonM;
  }
}
