package tools.debugger.session;

import java.util.List;

import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;

/**
 * Local manager only receives information regarding to the breakpoints relevant to it.
 *
 * @author carmentorres
 *
 */
public class LocalManager {

//local manager life cycle constants.
//all messages arrived in INITIAL
//correspond to initialization code, so we let them pass
public static final int INITIAL = 0;
//RUNNING is initially set when debugger manager
//calls start() on the local manager
public static final int RUNNING = 1;
//PAUSE is set due to a message breakpoint (implicit activation)
//or a pause command is received (explicit activation).
public static final int PAUSED = 2;
//to distinguish between the two paused states:
public static final int COMMAND = 3;
public static final int BREAKPOINT = 4;
public static final int STEPINTO = 5;
public static final int STEPOVER = 6;
public static final int STEPRETURN = 7;
public int debuggingState = INITIAL;
public int pausedState = INITIAL;

//stores base-level messages that cannot be process because actor is paused.
List<EventualMessage> inbox;
SourceSection sourceSection;
String fileName;

//List senderBreakpoints;
//List receiverBreakpoints;

//TODO create DebuggerException used to notify cases which shouldn't not happen.

public LocalManager(final Actor actor, final boolean debuggingSession, final DebuggerManager debuggerManagerFarRef) {

}

public boolean isStarted() {
  return this.debuggingState != INITIAL;
};

public boolean  isPaused() {
  return debuggingState == PAUSED;
  }
public boolean  isPausedByBreakpoint() {
  return pausedState == BREAKPOINT;
  }
public boolean  isInStepInto() {
  return pausedState == STEPINTO;
  }
public boolean  isInStepOver() {
  return pausedState == STEPOVER;
  }
public boolean  isInStepReturn() {
  return pausedState == STEPRETURN;
  }

/*public void addBreakpoint(final Breakpoint breakpoint) {

}


public void removeBreakpoint(final Breakpoint breakpoint) {

}*/

//TODO Breakpoint catalog
//matchesBreakpoint(catalog, rcv,msg)
//isBreakpointed

public void pauseAndBuffer() {

}

public void scheduleAllMessagesFromInbox() {

}

/**
 * you can re-schedule a message which is not breakpointed
 * but it is paused because of an explicit pause command!
 */
public void scheduleOneMessageFromInbox() {

}

//stepCommand

public void serve() {

}

//leave(msg) will put back the pausedState to initial
// in the case we are stepping into a breakpointed message
// so that send() stops marking outgoing messages as breakpointed
// when the turn executing a breakpointed message is ended.
public void leave() {

}

//TODO: args (rcv, msg)
public void send() {

}

public class InterfaceDebuggerManager{
  //evaluateCode
  //startInDebugMode
  //pause
  //resume
  //stepInto
  //stepReturn
  //stepOver
  //addBreakpoint
  //removeBreakpoint

}

}
