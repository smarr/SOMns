package tools.debugger.message;

import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SFarReference;
import som.vmobjects.SClass;
import tools.ObjectBuffer;
import tools.debugger.message.Message.OutgoingMessage;


@SuppressWarnings("unused")
public class MessageHistory extends OutgoingMessage {
  private final MessageData[] messages;
  private final FarRefData[]  actors;

  protected MessageHistory(final FarRefData[] actors, final MessageData[] message) {
    this.actors   = actors;
    this.messages = message;
  }

  protected static class FarRefData {
    private final String id;
    private final String typeName;

    protected FarRefData(final String id, final String typeName) {
      this.id       = id;
      this.typeName = typeName;
    }
  }

  protected static class MessageData {
    private final String id;
    private final String senderId;
    private final String targetId;

    protected MessageData(final String id, final String senderId, final String targetId) {
      this.id = id;
      this.senderId = senderId;
      this.targetId = targetId;
    }
  }

  public static MessageHistory create(final Map<SFarReference, String> actorsToIds,
      final ObjectBuffer<ObjectBuffer<ObjectBuffer<EventualMessage>>> messagesPerThread,
      final Map<Actor, String> actorObjsToIds) {
    FarRefData[] actors = new FarRefData[actorsToIds.size()];
    int i = 0;
    for (Entry<SFarReference, String> e : actorsToIds.entrySet()) {
      actors[i] = create(e.getValue(), e.getKey());
      i += 1;
    }

    return new MessageHistory(actors, messages(messagesPerThread, actorObjsToIds));
  }

  private static FarRefData create(final String id, final SFarReference a) {
    Object value = a.getValue();
    assert value instanceof SClass;
    SClass actorClass = (SClass) value;
    return new FarRefData(id, actorClass.getName().getString());
  }

  private static MessageData[] messages(
      final ObjectBuffer<ObjectBuffer<ObjectBuffer<EventualMessage>>> messagesPerThread,
      final Map<Actor, String> actorObjsToIds) {
    ArrayList<MessageData> messages = new ArrayList<>();
    int mId = 0;
    for (ObjectBuffer<ObjectBuffer<EventualMessage>> perThread : messagesPerThread) {
      for (ObjectBuffer<EventualMessage> perBatch : perThread) {
        for (EventualMessage m : perBatch) {
          assert actorObjsToIds.containsKey(m.getSender());
          assert actorObjsToIds.containsKey(m.getTarget());

          messages.add(new MessageData("m-" + mId,
              actorObjsToIds.get(m.getSender()),
              actorObjsToIds.get(m.getTarget())));
          mId += 1;
        }
      }
    }
    return messages.toArray(new MessageData[0]);
  }
}
