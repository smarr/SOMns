package tools.debugger.entities;

import tools.TraceData;

public class TraceSemantics {
  public enum Activity {
    PROCESS(EntityType.PROCESS, TraceData.PROCESS_CREATION, TraceData.PROCESS_COMPLETION),
    ACTOR(EntityType.ACTOR,     TraceData.ACTOR_CREATION),
    TASK(EntityType.TASK,       TraceData.TASK_SPAWN),
    THREAD(EntityType.THREAD,   TraceData.THREAD_SPAWN);

    private final EntityType type;
    private final byte creationMarker;
    private final byte completionMarker;

    Activity(final EntityType type, final int creationMarker) {
      this(type, creationMarker, 0);
    }

    Activity(final EntityType type, final int creationMarker,
        final int completionMarker) {
      this.type = type;
      this.creationMarker   = (byte) creationMarker;
      this.completionMarker = (byte) completionMarker;
    }

    public byte getId()               { return type.id; }
    public byte getCreationMarker()   { return creationMarker; }
    public byte getCompletionMarker() { return completionMarker; }

    public int getCreationSize()   { return 11 + SOURCE_SECTION_SIZE; }
    public int getCompletionSize() { return 1; }
  }

  public enum DynamicScope {
    TURN(EntityType.ACT_MSG,            TraceData.TURN_START,        TraceData.TURN_END),
    MONITOR(EntityType.MONITOR,         TraceData.MONITOR_ENTER,     TraceData.MONITOR_EXIT),
    TRANSACTION(EntityType.TRANSACTION, TraceData.TRANSACTION_START, TraceData.TRANSACTION_END);

    private final EntityType type;
    private final byte startMarker;
    private final byte endMarker;

    DynamicScope(final EntityType type, final int startMarker, final int endMarker) {
      this.type = type;
      this.startMarker = (byte) startMarker;
      this.endMarker   = (byte) endMarker;
    }

    public byte getId()          { return type.id; }
    public byte getStartMarker() { return startMarker; }
    public byte getEndMarker()   { return endMarker;   }

    public int getStartSize() { return 9 + SOURCE_SECTION_SIZE; }
    public int getEndSize()   { return 1; }
  }

  public enum PassiveEntity {
    CHANNEL(EntityType.CHANNEL, TraceData.CHANNEL_CREATION),
    CHANNEL_MSG(EntityType.CH_MSG),
    ACTOR_MSG(EntityType.ACT_MSG),
    PROMISE(EntityType.PROMISE);

    private final EntityType type;

    PassiveEntity(final EntityType type) {
      this(type, 0);
    }

    PassiveEntity(final EntityType type, final int creationMarker) {
      this.type = type;
    }

    public byte getId()          { return type.id; }
    public int getCreationSize() { return 9 + SOURCE_SECTION_SIZE; }
  }

  public enum SendOp {
    ACTOR_MSG(TraceData.ACTOR_MSG_SEND,          EntityType.ACT_MSG, EntityType.ACTOR),
    CHANNEL_SEND(TraceData.CHANNEL_MESSAGE_SEND, EntityType.CH_MSG,  EntityType.CHANNEL);

    private final byte id;
    private final EntityType entity;
    private final EntityType target;

    SendOp(final byte id, final EntityType entity, final EntityType target) {
      this.id = id;
      this.entity = entity;
      this.target = target;
    }

    public byte getId() { return id; }
    public EntityType getEntity() { return entity; }
    public EntityType getTarget() { return target; }
    public int getSize() { return 17; }
  }

  public enum ReceiveOp {
    CHANNEL_RCV(TraceData.CHANNEL_MESSAGE_RCV, EntityType.CHANNEL),
    TASK_JOIN(TraceData.TASK_JOIN,             EntityType.TASK),
    THREAD_JOIN(TraceData.THREAD_JOIN,         EntityType.THREAD);

    private final byte id;
    private final EntityType source;

    ReceiveOp(final byte id, final EntityType source) {
      this.id = id;
      this.source = source;
    }

    public byte getId() { return id; }
    public EntityType getSource() { return source; }
    public int getSize() { return 9; }
  }

  public enum Implementation {

  }
}
