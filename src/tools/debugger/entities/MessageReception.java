package tools.debugger.entities;

public enum MessageReception {
    MESSAGE_RCV(Marker.ACTOR_MSG_RECEIVE, EntityType.ACTOR);

    private final byte       id;
    private final EntityType source;

    MessageReception(final byte id,  final EntityType source) {
        this.id = id;
        this.source = source;
    }

    public byte getId() {
        return id;
    }

    public EntityType getSource() {
        return source;
    }

    public int getSize() {
        return 9;
    }
}
