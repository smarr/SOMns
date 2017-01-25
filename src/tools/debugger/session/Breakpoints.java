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
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;

import som.interpreter.actors.ReceivedRootNode;
import tools.SourceCoordinate.FullSourceCoordinate;
import tools.actors.Tags.ExpressionBreakpoint;
import tools.debugger.WebDebugger;


public class Breakpoints {

  private final DebuggerSession debuggerSession;

  /**
   * Breakpoints directly managed by Truffle.
   */
  private final Map<BreakpointInfo, Breakpoint> truffleBreakpoints;

  /**
   * MessageReceiverBreakpoints, manually managed by us (instead of Truffle).
   */
  private final Map<FullSourceCoordinate, BreakpointEnabling<MessageReceiverBreakpoint>> receiverBreakpoints;


  /**
   * PromiseResolverBreakpoint, manually managed by us (instead of Truffle).
   */
  private final Map<FullSourceCoordinate, BreakpointEnabling<PromiseResolverBreakpoint>> promiseResolverBreakpoints;

  /**
   * PromiseResolutionBreakpoint, manually managed by us (instead of Truffle).
   */
  private final Map<FullSourceCoordinate, BreakpointEnabling<PromiseResolutionBreakpoint>> promiseResolutionBreakpoints;

  /** Manually managed by us, instead of Truffle. */
  private final Map<FullSourceCoordinate, BreakpointEnabling<ChannelOppositeBreakpoint>> channelOppositeBreakpoint;

  public Breakpoints(final Debugger debugger, final WebDebugger webDebugger) {
    this.truffleBreakpoints           = new HashMap<>();
    this.receiverBreakpoints          = new HashMap<>();
    this.promiseResolverBreakpoints   = new HashMap<>();
    this.promiseResolutionBreakpoints = new HashMap<>();
    this.channelOppositeBreakpoint    = new HashMap<>();
    this.debuggerSession = debugger.startSession(webDebugger);
  }

  public void doSuspend(final MaterializedFrame frame, final SteppingLocation steppingLocation) {
    debuggerSession.doSuspend(frame, steppingLocation);
  }

  public void prepareSteppingUntilNextRootNode() {
    debuggerSession.prepareSteppingUntilNextRootNode();
  }

  public void prepareSteppingAfterNextRootNode() {
    debuggerSession.prepareSteppingAfterNextRootNode();
  }

  public synchronized void addOrUpdate(final LineBreakpoint bId) {
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
  }

  public synchronized void addOrUpdate(final MessageSenderBreakpoint bId) {
    saveTruffleBasedBreakpoints(bId, ExpressionBreakpoint.class);
  }

  public synchronized void addOrUpdate(final AsyncMessageReceiverBreakpoint bId) {
    Breakpoint bp = saveTruffleBasedBreakpoints(bId, RootTag.class);
    bp.setCondition(BreakWhenActivatedByAsyncMessage.INSTANCE);
  }

  public synchronized void addOrUpdate(final MessageReceiverBreakpoint bId) {
    saveBreakpoint(bId, receiverBreakpoints);
  }

  public synchronized void addOrUpdate(final PromiseResolverBreakpoint bId) {
    saveBreakpoint(bId, promiseResolverBreakpoints);
  }

  public synchronized void addOrUpdate(final PromiseResolutionBreakpoint bId) {
    saveBreakpoint(bId, promiseResolutionBreakpoints);
  }

  public synchronized void addOrUpdate(final ChannelOppositeBreakpoint bId) {
    saveBreakpoint(bId, channelOppositeBreakpoint);
  }

  private Breakpoint saveTruffleBasedBreakpoints(final SectionBreakpoint bId, final Class<?> tag) {
    Breakpoint bp = truffleBreakpoints.get(bId);
    if (bp == null) {
      bp = Breakpoint.newBuilder(bId.getCoordinate().uri).
          lineIs(bId.getCoordinate().startLine).
          columnIs(bId.getCoordinate().startColumn).
          sectionLength(bId.getCoordinate().charLength).
          tag(tag).
          build();
      debuggerSession.install(bp);
      truffleBreakpoints.put(bId, bp);
    }
    bp.setEnabled(bId.isEnabled());
    return bp;
  }

  private <T extends SectionBreakpoint> void saveBreakpoint(final T bId,
      final Map<FullSourceCoordinate, BreakpointEnabling<T>> breakpoints) {
    FullSourceCoordinate coord = bId.getCoordinate();
    BreakpointEnabling<T> existingBP = breakpoints.get(coord);
    if (existingBP == null) {
      existingBP = new BreakpointEnabling<T>(bId);
      breakpoints.put(coord, existingBP);
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

 public synchronized BreakpointEnabling<MessageReceiverBreakpoint> getReceiverBreakpoint(
      final FullSourceCoordinate section) {
    return receiverBreakpoints.computeIfAbsent(section,
        ss -> new BreakpointEnabling<>(new MessageReceiverBreakpoint(false, section)));
  }

  public synchronized BreakpointEnabling<PromiseResolverBreakpoint> getPromiseResolverBreakpoint(
      final FullSourceCoordinate section) {
    return promiseResolverBreakpoints.computeIfAbsent(section,
        ss -> new BreakpointEnabling<>(new PromiseResolverBreakpoint(false, section)));
  }

  public synchronized BreakpointEnabling<PromiseResolutionBreakpoint> getPromiseResolutionBreakpoint(
      final FullSourceCoordinate section) {
    return promiseResolutionBreakpoints.computeIfAbsent(section,
        ss -> new BreakpointEnabling<>(new PromiseResolutionBreakpoint(false, section)));
  }

  public synchronized BreakpointEnabling<ChannelOppositeBreakpoint> getOppositeBreakpoint(
      final FullSourceCoordinate section) {
    return channelOppositeBreakpoint.computeIfAbsent(section,
        ss -> new BreakpointEnabling<>(new ChannelOppositeBreakpoint(false, section)));
  }
}
