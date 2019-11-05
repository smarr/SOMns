package tools.debugger.message;

public class PauseActorResponse extends Message.OutgoingMessage {
    private long actorId;

    public PauseActorResponse(long actorId) {
        this.actorId = actorId;
    }

    public static Message create(long pausedActorId) {
        return new PauseActorResponse(pausedActorId);
    }
}
