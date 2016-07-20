package tools.debugger.session;

import java.util.Map;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import tools.ObjectBuffer;
import tools.debugger.Breakpoints.BreakpointId;

 /**
 *
 * This class is responsible for:
 * - instrumenting message sending, processing
 * and reception.
 * - receives information regarding to the breakpoints relevant to it.
 *
 * @author carmentorres
 *
 */
public class LocalManager {

  /**
   * Local manager life cycle.
   *
   */
  public enum State {
    INITIAL, // all messages arrived in INITIAL correspond to initialization
             // code, so we let them pass
    RUNNING, PAUSED, // is set due to a message breakpoint (implicit activation)
                     // or a pause command is received (explicit activation).
    COMMAND, // to distinguish between the two paused states
    BREAKPOINT, STEPINTO, STEPOVER, STEPRETURN,
  }

  public State debuggingState = State.INITIAL;
  public State pausedState = State.INITIAL;

  /**
   * corresponding actor for this localManager.
   */
  private Actor actor;

  /**
   * stores base-level messages that cannot be process because actor is paused.
   */
  private ObjectBuffer<EventualMessage> inbox;

  /**
   * filename to debug.
   */
  private String  fileName;
  /**
   * Source section of the filename being debug.
   */
  private SourceSection sourceSection;

  Map<BreakpointId, Breakpoint> senderBreakpoints;
  Map<BreakpointId, Breakpoint> receiverBreakpoints;

  public LocalManager(final Actor actor) {
    this.actor = actor;
    this.inbox = new ObjectBuffer<>(16);
    this.debuggingState = State.INITIAL;
    this.pausedState = State.INITIAL;
  }

  public boolean isStarted() {
    return this.debuggingState != State.INITIAL;
  };

  public boolean isPaused() {
    return debuggingState == State.PAUSED;
  }

  public boolean isPausedByBreakpoint() {
    return pausedState == State.BREAKPOINT;
  }

  public boolean isInStepInto() {
    return pausedState == State.STEPINTO;
  }

  public boolean isInStepOver() {
    return pausedState == State.STEPOVER;
  }

  public boolean isInStepReturn() {
    return pausedState == State.STEPRETURN;
  }


   public void addBreakpoint(final Breakpoint b, final EventualMessage msg) {


     //todo...
     //senderBreakpoints.put(key, value);
     //msg.setPause(true);
    }


    public void removeBreakpoint(final Breakpoint b) {
     // senderBreakpoints.remove(b);
    }


  /**
   * Check if the message is breakpointed
   *
   * @param msg
   * @return
   */
  public boolean isBreakpointed(final EventualMessage msg) {

    return false;
  }


  public void pauseAndBuffer(final EventualMessage msg, final State state) {
    this.actor.getMailbox().append(msg);

    if (isStarted()) {
      //updateInbox(this.actor, msg);
      this.debuggingState = State.PAUSED;
    }
    this.pausedState = state;
  }

  // stepCommand

  // leave(msg) will put back the pausedState to initial
  // in the case we are stepping into a breakpointed message
  // so that send() stops marking outgoing messages as breakpointed
  // when the turn executing a breakpointed message is ended.
  public void leave() {

  }

  // TODO: args (rcv, msg)
  public void send(final EventualMessage msg) {

  }
/**
 * Save message in actor mailbox.
 *
 * @param msg
 */
  public void schedule(final EventualMessage msg) {
    if (isPaused()) {
        if (isInStepInto() || isInStepOver()) {
          // This means we got the message breakpointed that needs to be executed
          // or that we are paused in a message, and the user click on step over
        //  updateInbox(this.actor,msg);

          //add message in the queue of the actor
          this.actor.getMailbox().append(msg);
        } else if (isInStepReturn()) {
//
        } else {
        //here for all messages arriving to a paused actor
          pauseAndBuffer(msg, pausedState);
        }
    }
  }

  public void serve() {

  }

  public void pause() {
    this.debuggingState = State.PAUSED;
    this.pausedState = State.COMMAND;
    //actorPause(actorId, actorState)
  }

 public void resume() {
   this.debuggingState = State.RUNNING;
   this.pausedState = State.INITIAL;
   //actorResume(actorId)
  }

 public void stepInto() {
   stepCommand(State.STEPINTO);
 }

 public void stepOver() {
   stepCommand(State.STEPOVER);
 }

 public void stepReturn() {
   stepCommand(State.STEPRETURN);
 }

 public void stepCommand(final State step) {
   if (isPaused()) {
     this.pausedState = step;
   }
   scheduleOneMessageFromInbox();
 }

//you can re-schedule a message which is not breakpointed
 // but it is paused because of an explicit pause command!
 public void scheduleOneMessageFromInbox() {

 }

}
