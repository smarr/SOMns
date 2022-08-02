package tools.debugger.message;
import org.java_websocket.WebSocket;
import tools.debugger.FrontendConnector;
import tools.debugger.frontend.Suspension;
import tools.debugger.message.Message.IncommingMessage;
public class RestartFrame extends IncommingMessage {
    private final int currentFrameId;

    public RestartFrame(int currentFrameId) {
        this.currentFrameId = currentFrameId;
    }

    @Override
    public void process(FrontendConnector connector, WebSocket conn) {
        Suspension suspension= connector.getSuspension(0);
        int realId = currentFrameId + suspension.getFrameSkipCount();
        connector.restartFrame(suspension,suspension.getStackFrames().get(realId));
    }
}
