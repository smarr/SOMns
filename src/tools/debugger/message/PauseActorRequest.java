package tools.debugger.message;

import org.java_websocket.WebSocket;
import som.interpreter.actors.Actor;
import tools.debugger.FrontendConnector;

public class PauseActorRequest extends Message.IncommingMessage {
    private final long actorId;

    public PauseActorRequest() {
        actorId = -1;
    }

    public PauseActorRequest(long actorId) {
        this.actorId = actorId;
    }

    @Override
    public void process(FrontendConnector connector, WebSocket conn) {
        Actor actor = connector.getActorById(this.actorId);
        assert actor != null : "Failed to get actor for activityId: " + this.actorId;
        actor.setStepToNextTurn(true);
        FrontendConnector.log("[DEBUGGER] Actor "+actor.getId() +" will pause before processing the next message.");
    }
}
