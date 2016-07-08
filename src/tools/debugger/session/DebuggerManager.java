package tools.debugger.session;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import som.interpreter.actors.Actor;
import som.interpreter.actors.SFarReference;

/**
 * Manager for the debugging session.
 *
 * @author carmentorres
 *
 */
public class DebuggerManager {

  /**
   * list of connected actors in the debugging session.
   */
  Map<SFarReference, String> actorsToIds;

  /**
   * breakpoints that should be active.
   */
  //List<Breakpoint> breakpoints;

  /**
   * local manager by actor.
   */
  Map<Actor, LocalManager> actorLocalManager;

  public DebuggerManager() {
    //ObjectBuffer<ObjectBuffer<SFarReference>> createdActorsPerThread = ActorExecutionTrace.getAllCreateActors();
    //actorsToIds = ActorExecutionTrace.createActorMap(createdActorsPerThread);

    //breakpoints = new ArrayList<Breakpoint>();
    actorLocalManager = new HashMap<>();
  }

//check: move createActorMap method from WebDebugger to ActorExecutionThread to get the map of actors...
  public void removeActorFromDataStructures(final Actor actor, final LocalManager lm) {

    for (Entry<SFarReference, String> e : actorsToIds.entrySet()) {
      Actor a = e.getKey().getActor();
      assert !a.equals(actor);
      actorsToIds.remove(a);
    }

    //remove localManager corresponding to that actor
    if (actorLocalManager.containsKey(actor)) {
      actorLocalManager.remove(actor);
    }
  }

  public void addActorToDataStructures(final Actor actor, final LocalManager lm) {
    //createdActorsPerThread is updated in Actor 54 traceActorsExceptMainOne()
    actorLocalManager.put(actor, lm);
  }

  //param actorSetId is not needed
  /*public void notifyBreakpoint(final Breakpoint breakpoint, final boolean isAddition) {
    for (Actor actor : actorLocalManager.keySet()) {
      LocalManager lm = actorLocalManager.get(actor);
      if (isAddition) {
        //@Debug in remed
       lm.addBreakpoint(breakpoint);
      } else {
        //@Debug in remed
        lm.removeBreakpoint(breakpoint);
      }
    }
  }*/

  public void notifyCommand(final Actor actor, final String message) {
    LocalManager lm = actorLocalManager.get(actor);
    if (lm != null) {
    //TODO: frLocalManager <+ message; //apply the message

    }

  }

  /**
   * Defines all methods that can be invoked by the debugger front-end via
   * the command listener object.
   *
   * @author carmentorres
   *
   */
  public class LocalInterface{
  //listLocalManagers
    //getLocalManagerById
    //setupDebugSession
    //loadMainCode
    //pauseActor
    //resumeActor
    //stepInto
    //stepOver
    //stepReturn

    //setBreakpoint
    //clearBreakpoint
    //breakpointActiveOn

    //exportDebugSession---for distribution

  }

/**
 * Defines all methods that can be invoked by the local manager to update the debugger front-end.
 *
 * @author carmentorres
 *
 */
  public class RemoteInterface{
    //actorStarted
    //actorPaused
    //actorResumed
    //updateInbox
    //updateMessageSent
  }


}
