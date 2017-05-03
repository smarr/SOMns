package tools.debugger.message;

import org.java_websocket.WebSocket;

import tools.debugger.FrontendConnector;
import tools.debugger.entities.SteppingType;
import tools.debugger.frontend.Suspension;
import tools.debugger.message.Message.IncommingMessage;


public class StepMessage extends IncommingMessage {
  private final long activityId;
  private final SteppingType step;

  /**
   * Note: meant for serialization.
   */
  public StepMessage() {
    activityId = -1;
    step       = null;
  }

  @Override
  public void process(final FrontendConnector connector, final WebSocket conn) {
    Suspension susp = connector.getSuspension(activityId);
    step.process(susp);
    susp.resume();
  }
}
