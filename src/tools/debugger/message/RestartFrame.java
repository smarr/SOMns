package tools.debugger.message;
import org.java_websocket.WebSocket;
import tools.debugger.FrontendConnector;
import tools.debugger.frontend.Suspension;
import tools.debugger.message.Message.IncommingMessage;
public class RestartFrame extends IncommingMessage {
    private final int frameId;

    public RestartFrame(int currentFrameId) {
        this.frameId = currentFrameId;
    }

    @Override
    public void process(FrontendConnector connector, WebSocket conn) {
        Suspension suspension= connector.getSuspension(0);
        int skipCount = suspension.getFrameSkipCount();
        skipCount = skipCount > 0 ? skipCount - 1 : skipCount;
        int realId = frameId + skipCount;
        System.out.print("Restarted Frame Request " + frameId + "After skipping " + realId);
        connector.restartFrame(suspension,suspension.getStackFrames().get(realId));
    }
}
