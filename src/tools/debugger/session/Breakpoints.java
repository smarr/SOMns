package tools.debugger.session;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Breakpoint.SimpleCondition;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.DebuggerSession.SteppingLocation;
import com.oracle.truffle.api.frame.MaterializedFrame;

import som.interpreter.actors.ReceivedRootNode;
import tools.SourceCoordinate.FullSourceCoordinate;
import tools.debugger.WebDebugger;


public class Breakpoints {

  private final DebuggerSession debuggerSession;

  /**
   * Breakpoints directly managed by Truffle.
   */
  private final Map<BreakpointInfo, Breakpoint> truffleBreakpoints;

  /**
   * MessageReceiveBreakpoints, manually managed by us (instead of Truffle).
   */
  private final Map<FullSourceCoordinate, BreakpointEnabling<MessageReceiveBreakpoint>> receiverBreakpoints;

  public Breakpoints(final Debugger debugger, final WebDebugger webDebugger) {
    this.truffleBreakpoints = new HashMap<>();
    this.receiverBreakpoints = new HashMap<>();
    this.debuggerSession = debugger.startSession(webDebugger);
  }

  public void doSuspend(final MaterializedFrame frame, final SteppingLocation steppingLocation) {
    debuggerSession.doSuspend(frame, steppingLocation);
  }

  public void prepareSteppingUntilNextRootNode() {
    debuggerSession.prepareSteppingUntilNextRootNode();
  }

  public Breakpoint addOrUpdate(final LineBreakpoint bId) {
    Breakpoint bp = truffleBreakpoints.get(bId);

    if (bp == null) {
      WebDebugger.log("LineBreakpoint: " + bId);
      bp = Breakpoint.newBuilder(bId.getURI()).
          lineIs(bId.getLine()).
          build();
      debuggerSession.install(bp);
      truffleBreakpoints.put(bId, bp);
    }
    bp.setEnabled(bId.isEnabled());
    return bp;
  }

  public Breakpoint addOrUpdate(final MessageSenderBreakpoint bId) {
    Breakpoint bp = truffleBreakpoints.get(bId);
    if (bp == null) {
      WebDebugger.log("SetSectionBreakpoint: " + bId);
      bp = Breakpoint.newBuilder(bId.getCoordinate().uri).
          lineIs(bId.getCoordinate().startLine).
          columnIs(bId.getCoordinate().startColumn).
          sectionLength(bId.getCoordinate().charLength).
          build();
      debuggerSession.install(bp);
      truffleBreakpoints.put(bId, bp);
    }
    bp.setEnabled(bId.isEnabled());
    return bp;
  }

  public Breakpoint addOrUpdate(final AsyncMessageReceiveBreakpoint bId) {
    Breakpoint bp = truffleBreakpoints.get(bId);

    if (bp == null) {
      WebDebugger.log("RootBreakpoint: " + bId);
      bp = Breakpoint.newBuilder(bId.getCoordinate().uri).
          lineIs(bId.getCoordinate().startLine).
          columnIs(bId.getCoordinate().startColumn).
          sectionLength(bId.getCoordinate().charLength).
          build();
      bp.setCondition(BreakWhenActivatedByAsyncMessage.INSTANCE);
      debuggerSession.install(bp);
      truffleBreakpoints.put(bId, bp);
    }
    bp.setEnabled(bId.isEnabled());
    return bp;
  }

  public synchronized void addOrUpdate(final MessageReceiveBreakpoint bId) {
    FullSourceCoordinate coord = bId.getCoordinate();
    BreakpointEnabling<MessageReceiveBreakpoint> existingBP = receiverBreakpoints.get(coord);
    if (existingBP == null) {
      existingBP = new BreakpointEnabling<MessageReceiveBreakpoint>(bId);
      receiverBreakpoints.put(coord, existingBP);
    } else {
      existingBP.setEnabled(bId.isEnabled());
    }
  }

  private static final class BreakWhenActivatedByAsyncMessage implements SimpleCondition {
    static BreakWhenActivatedByAsyncMessage INSTANCE = new BreakWhenActivatedByAsyncMessage();

    private BreakWhenActivatedByAsyncMessage() { }

    @Override
    public boolean evaluate() {
      RootCallTarget ct = (RootCallTarget) Truffle.getRuntime().getCallerFrame().getCallTarget();
      return (ct.getRootNode() instanceof ReceivedRootNode);
    }
  }

  public synchronized BreakpointEnabling<MessageReceiveBreakpoint> getReceiverBreakpoint(
      final FullSourceCoordinate section) {
    return receiverBreakpoints.computeIfAbsent(section,
        ss -> new BreakpointEnabling<>(new MessageReceiveBreakpoint(false, section)));
  }
}
