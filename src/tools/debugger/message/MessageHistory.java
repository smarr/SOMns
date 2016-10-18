package tools.debugger.message;

import java.util.ArrayList;

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
    private final long id;
    private final String typeName;

    protected FarRefData(final long id, final String typeName) {
      this.id       = id;
      this.typeName = typeName;
    }
  }

  protected static class MessageData {
    private final String id;
    private final long senderId;
    private final long targetId;

    protected MessageData(final String id, final long senderId, final long targetId) {
      this.id = id;
      this.senderId = senderId;
      this.targetId = targetId;
    }
  }

  public static MessageHistory create(final ObjectBuffer<ObjectBuffer<SFarReference>> actorList,
      final ObjectBuffer<ObjectBuffer<ObjectBuffer<EventualMessage>>> messagesPerThread) {
    ArrayList<FarRefData> actors = new ArrayList<>();
    for(ObjectBuffer<SFarReference> perThread : actorList){
      for (SFarReference e : perThread) {
        actors.add(create(e.getActor().getActorId(), e));
      }
    }
    return new MessageHistory(actors.toArray(new FarRefData[0]), messages(messagesPerThread));
  }

  private static FarRefData create(final long id, final SFarReference a) {
    Object value = a.getValue();
    assert value instanceof SClass;
    SClass actorClass = (SClass) value;
    return new FarRefData(id, actorClass.getName().getString());
  }

  private static MessageData[] messages(
      final ObjectBuffer<ObjectBuffer<ObjectBuffer<EventualMessage>>> messagesPerThread) {
    ArrayList<MessageData> messages = new ArrayList<>();
    int mId = 0;
    for (ObjectBuffer<ObjectBuffer<EventualMessage>> perThread : messagesPerThread) {
      for (ObjectBuffer<EventualMessage> perBatch : perThread) {
        for (EventualMessage m : perBatch) {
          messages.add(new MessageData("m-" + mId,
              m.getSender().getActorId(),
              m.getTarget().getActorId()));
          mId += 1;
        }
      }
    }
    return messages.toArray(new MessageData[0]);
  }
}
