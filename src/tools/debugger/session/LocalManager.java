package tools.debugger.session;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import tools.ObjectBuffer;
import tools.debugger.Breakpoints.SectionBreakpoint;
import tools.debugger.FrontendConnector.BreakpointLocation;

/**
 * This class is responsible for:
 *  - instrumenting message sending, processing and reception
 *  - receives information regarding to the breakpoints relevant to it.
 *
 * @author carmentorres
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

  public State                          debuggingState = State.INITIAL;
  public State                          pausedState    = State.INITIAL;

  /**
   * corresponding actor for this localManager.
   */
  private final Actor                         actor;

  /**
   * stores base-level messages that cannot be process because actor is paused.
   */
  private ObjectBuffer<EventualMessage> inbox;

  /**
   * filename to debug.
   */
  private URI                           fileName;

  final Map<BreakpointLocation, Breakpoint>   senderBreakpoints;
  final Map<BreakpointLocation, Breakpoint>   receiverBreakpoints;

  public LocalManager(final Actor actor) {
    this.actor = actor;
    this.inbox = new ObjectBuffer<EventualMessage>(16);
    this.debuggingState = State.INITIAL;
    this.pausedState = State.INITIAL;
    this.receiverBreakpoints = new HashMap<>();
    this.senderBreakpoints = new HashMap<>();
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

  public void setFileName(final URI fileName) {
    this.fileName = fileName;
  }

  public void addBreakpoint(final Breakpoint breakpoint,
      final BreakpointLocation bLocation, final boolean receiver) {
    if (receiver) {
      receiverBreakpoints.put(bLocation, breakpoint);
    } else {
      senderBreakpoints.put(bLocation, breakpoint);
    }
  }

  public void removeBreakpoint(final BreakpointLocation bLocation,
      final boolean receiver) {
    if (receiver) {
      receiverBreakpoints.remove(bLocation);
    } else {
      senderBreakpoints.remove(bLocation);
    }
  }

  /**
   * Check if the message is breakpointed.
   */
  public boolean isBreakpointed(final EventualMessage msg,
      final boolean receiver) {
    SourceSection source = msg.getTargetSourceSection();

    if (receiver) {
      if (!this.receiverBreakpoints.isEmpty()) {
        Set<BreakpointLocation> keys = this.receiverBreakpoints.keySet();
        for (BreakpointLocation breakpointLocation : keys) {
          SectionBreakpoint bId = (SectionBreakpoint) breakpointLocation
              .getId();

          SectionBreakpoint savedBreakpoint = new SectionBreakpoint(fileName,
              source.getStartLine(), source.getStartColumn(),
              source.getCharIndex());

          if (bId.equals(savedBreakpoint)) {
            return true;
          }
        }
      }
    } else { // sender
      if (!this.senderBreakpoints.isEmpty()) {
        Set<BreakpointLocation> keys = this.senderBreakpoints.keySet();
        for (BreakpointLocation breakpointLocation : keys) {
          SectionBreakpoint bId = (SectionBreakpoint) breakpointLocation
              .getId();

          SectionBreakpoint savedBreakpoint = new SectionBreakpoint(fileName,
              source.getStartLine(), source.getStartColumn(),
              source.getCharIndex());

          if (bId.equals(savedBreakpoint)) {
            return true;
          }

        }
      }
    }

    return false;
  }

  /**
   * Set debugging state to pause.
   */
  public void pauseAndBuffer(final EventualMessage msg, final State state) {

    this.inbox.append(msg);

    if (isStarted()) {

      if (!isPaused()) {
        // set actor Paused
      }
      // updateInbox(this.actor, msg);
      this.debuggingState = State.PAUSED;
    }
    this.pausedState = state;
  }

  // stepCommand

  // will put back the pausedState to initial
  // in the case we are stepping into a breakpointed message
  // so that send() stops marking outgoing messages as breakpointed
  // when the turn executing a breakpointed message is ended.
  public void leave(final EventualMessage msg, final boolean stopReceiver) {
    if (isInStepOver()) {
      this.pausedState = State.INITIAL;

      if (this.inbox.isEmpty()) {
        this.debuggingState = State.RUNNING;
        // actorResumed
      }
    }

    if (isInStepInto()) {
      // senderBreakpoints.remove(futureBreakpoint....
      this.pausedState = State.INITIAL;
      this.debuggingState = State.RUNNING;
      // actorResumed
    }

    if (stopReceiver) {
      // todo finish
      if (isInStepInto()) {
        this.pausedState = State.INITIAL;
      }
    }
  }

  /*
   * public void send(final EventualMessage msg) {
   *
   * }
   */

  /**
   * Save message in actor mailbox.
   *
   * @param msg
   */
  public void schedule(final EventualMessage msg, final boolean receiver) {
    if (isStarted()) {
      if (isPaused()) { // actor paused
        if (isInStepInto() || isInStepOver()) {
          // This means we got the message breakpointed that needs to be
          // executed
          // or that we are paused in a message, and the user click on step over

          // updateInbox..

          // add message in the queue of the actor
          this.actor.getMailbox().append(msg);
        } else if (isInStepReturn()) {
          // updateInbox..

          // means we got a futurized message that needs to be executed with a
          // conditional breakpoint.
          // TODO check if this is need it for this debugger
          installFutureBreakpoint(msg);

          this.actor.getMailbox().append(msg);

        } else {
          // here for all messages arriving to a paused actor
          pauseAndBuffer(msg, pausedState);
        }
      } else { // actor running
        // check whether the msg has a breakpoint
        boolean isBreakpointed = isBreakpointed(msg, receiver);
        if (isBreakpointed) {
          if (receiver == false) { // pausing at sender actor = PauseResolve
                                   // annotation in remed
            installFutureBreakpoint(msg);
            this.actor.getMailbox().append(msg);
          } else { // pausing at receiver
            pauseAndBuffer(msg, State.BREAKPOINT);
          }

        } else {

          this.actor.getMailbox().append(msg);
        }

      }
    } else { // actor doesn't started
      // check if it is ExternalMessage
      pauseAndBuffer(msg, State.INITIAL);
    }
  }

  private void installFutureBreakpoint(final EventualMessage msg) {
    // create a MessageResolution breakpoint from the message and add it to the
    // senderBreakpoints
  }

  public void serve() {
    // dequeue the message when reaches the beginning of the queue and execute
    // it
    // check if it is breakpointed
  }

  public void pause() {
    this.debuggingState = State.PAUSED;
    this.pausedState = State.COMMAND;
    // actorPause(actorId, actorState)
  }

  public void resume() {
    this.debuggingState = State.RUNNING;
    this.pausedState = State.INITIAL;
    // actorResume(actorId)
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

  // you can re-schedule a message which is not breakpointed
  // but it is paused because of an explicit pause command!
  public void scheduleOneMessageFromInbox() {
    int size = this.inbox.size();

    if (size > 0) {
      EventualMessage msg = this.inbox.iterator().next();

      // todo check on the length of the inbox, maybe it was the last message.
      schedule(msg, true);
    }
  }
}
