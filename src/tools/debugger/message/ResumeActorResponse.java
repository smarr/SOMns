package tools.debugger.message;

public class ResumeActorResponse extends Message.OutgoingMessage {
    private long actorId;

    public ResumeActorResponse(long actorId) {
        this.actorId = actorId;
    }

    public static Message create(long runningActorId) {
        return new ResumeActorResponse(runningActorId);
    }
}
