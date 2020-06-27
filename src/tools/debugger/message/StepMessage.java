package tools.debugger.message;

import org.java_websocket.WebSocket;

import som.interpreter.actors.Actor;
import tools.debugger.FrontendConnector;
import tools.debugger.entities.SteppingType;
import tools.debugger.frontend.Suspension;
import tools.debugger.message.Message.IncommingMessage;


public class StepMessage extends IncommingMessage {
  private final long         activityId;
  private final SteppingType step;

  /**
   * Note: meant for serialization.
   */
  StepMessage() {
    activityId = -1;
    step = null;
  }

  @Override
  public void process(final FrontendConnector connector, final WebSocket conn) {
    Suspension susp = connector.getSuspension(activityId);

    if (susp != null && susp.getEvent() != null) {
      step.process(susp);
      susp.resume();
      if (step != SteppingType.RESUME) {
        FrontendConnector.log("[DEBUGGER] Executing "+ step.name +" for actor "+activityId);
      }
    } else if(step == SteppingType.RESUME) {
      //notify frontend the actor that can be resumed in the GUI because the actor is not suspended,
      //this is needed for actors that are paused explicitly in the frontend, and they are not actually suspended yet (mailbox is empty)
      Actor actor = connector.getActorById(this.activityId);
      assert actor != null : "Failed to get actor for activityId: " + this.activityId;
      //reset step next turn flag to false
      actor.setStepToNextTurn(false);
    } else {
      FrontendConnector.log("[DEBUGGER]: Failed to get suspension for activityId: " + activityId);
    }

    sendResumeMessage(connector);
  }

  private void sendResumeMessage(FrontendConnector connector) {
    if(step == SteppingType.RESUME || step == SteppingType.RETURN_FROM_TURN_TO_PROMISE_RESOLUTION) {
      //send resume message
      connector.sendResumeActorResponse(this.activityId);
      FrontendConnector.log("[DEBUGGER] Resuming actor "+activityId);
    }
  }
}
