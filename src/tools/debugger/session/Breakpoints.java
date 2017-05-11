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
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.interpreter.actors.ReceivedRootNode;
import som.vm.VmSettings;
import tools.SourceCoordinate;
import tools.SourceCoordinate.FullSourceCoordinate;
import tools.concurrency.Tags.ExpressionBreakpoint;
import tools.debugger.WebDebugger;
import tools.debugger.entities.BreakpointType;
import tools.debugger.entities.SteppingType;
import tools.debugger.nodes.AbstractBreakpointNode;
import tools.debugger.nodes.BreakpointNodeGen;
import tools.debugger.nodes.DisabledBreakpointNode;


public class Breakpoints {

  private final DebuggerSession debuggerSession;

  /**
   * Breakpoints directly managed by Truffle.
   */
  private final Map<BreakpointInfo, Breakpoint> truffleBreakpoints;

  /**
   * MessageReceiverBreakpoints, manually managed by us (instead of Truffle).
   */
  private final Map<FullSourceCoordinate, BreakpointEnabling<SectionBreakpoint>> receiverBreakpoints;


  /**
   * PromiseResolverBreakpoint, manually managed by us (instead of Truffle).
   */
  private final Map<FullSourceCoordinate, BreakpointEnabling<SectionBreakpoint>> promiseResolverBreakpoints;

  /**
   * PromiseResolutionBreakpoint, manually managed by us (instead of Truffle).
   */
  private final Map<FullSourceCoordinate, BreakpointEnabling<SectionBreakpoint>> promiseResolutionBreakpoints;

  /** Manually managed by us, instead of Truffle. */
  private final Map<FullSourceCoordinate, BreakpointEnabling<SectionBreakpoint>> channelOppositeBreakpoints;
  private final Map<FullSourceCoordinate, BreakpointEnabling<SectionBreakpoint>> onExecutionBreakpoints;

  private final Map<FullSourceCoordinate, BreakpointEnabling<SectionBreakpoint>> beforeCommitBreakpoints;

  public Breakpoints(final Debugger debugger, final WebDebugger webDebugger) {
    this.truffleBreakpoints           = new HashMap<>();
    this.receiverBreakpoints          = new HashMap<>();
    this.promiseResolverBreakpoints   = new HashMap<>();
    this.promiseResolutionBreakpoints = new HashMap<>();
    this.channelOppositeBreakpoints   = new HashMap<>();
    this.onExecutionBreakpoints       = new HashMap<>();
    this.beforeCommitBreakpoints      = new HashMap<>();
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

  public synchronized void addOrUpdateBeforeExpression(final SectionBreakpoint bId) {
    saveTruffleBasedBreakpoints(bId, ExpressionBreakpoint.class, null);
  }

  public synchronized void addOrUpdateAfterExpression(final SectionBreakpoint bId) {
    // TODO: does this work???
    saveTruffleBasedBreakpoints(bId, ExpressionBreakpoint.class, SteppingLocation.AFTER_STATEMENT);
  }

  public synchronized void addOrUpdateMessageReceiver(final SectionBreakpoint bId) {
    saveBreakpoint(bId, receiverBreakpoints, SteppingType.STEP_TO_MESSAGE_RECEIVER);
  }

  public synchronized void addOrUpdateAsyncBefore(final SectionBreakpoint bId) {
    Breakpoint bp = saveTruffleBasedBreakpoints(bId, RootTag.class, null);
    bp.setCondition(BreakWhenActivatedByAsyncMessage.INSTANCE);
  }

  public synchronized void addOrUpdateAsyncAfter(final SectionBreakpoint bId) {
    Breakpoint bp = saveTruffleBasedBreakpoints(bId, RootTag.class, SteppingLocation.AFTER_STATEMENT);
    bp.setCondition(BreakWhenActivatedByAsyncMessage.INSTANCE);
  }

  public synchronized void addOrUpdatePromiseResolver(final SectionBreakpoint bId) {
    saveBreakpoint(bId, promiseResolverBreakpoints, SteppingType.STEP_TO_PROMISE_RESOLVER);
  }

  public synchronized void addOrUpdatePromiseResolution(final SectionBreakpoint bId) {
    saveBreakpoint(bId, promiseResolutionBreakpoints, SteppingType.STEP_TO_PROMISE_RESOLUTION);
  }

  public synchronized void addOrUpdateChannelOpposite(final SectionBreakpoint bId,
      final SteppingType type) {
    saveBreakpoint(bId, channelOppositeBreakpoints, type);
  }

  public synchronized void addOrUpdateActivityOnExec(final SectionBreakpoint bId) {
    saveBreakpoint(bId, onExecutionBreakpoints, SteppingType.STEP_INTO_ACTIVITY);
  }

  public synchronized void addOrUpdateBeforeCommit(final SectionBreakpoint bId) {
    saveBreakpoint(bId, beforeCommitBreakpoints, SteppingType.STEP_TO_COMMIT);
  }

  private Breakpoint saveTruffleBasedBreakpoints(final SectionBreakpoint bId, final Class<?> tag, final SteppingLocation sl) {
    Breakpoint bp = truffleBreakpoints.get(bId);
    if (bp == null) {
      bp = Breakpoint.newBuilder(bId.getCoordinate().uri).
          lineIs(bId.getCoordinate().startLine).
          columnIs(bId.getCoordinate().startColumn).
          sectionLength(bId.getCoordinate().charLength).
          tag(tag).
          steppingLocation(sl).
          build();
      debuggerSession.install(bp);
      truffleBreakpoints.put(bId, bp);
    }
    bp.setEnabled(bId.isEnabled());
    return bp;
  }

  private void saveBreakpoint(final SectionBreakpoint bId,
      final Map<FullSourceCoordinate, BreakpointEnabling<SectionBreakpoint>> breakpoints,
      final SteppingType type) {
    FullSourceCoordinate coord = bId.getCoordinate();
    BreakpointEnabling<SectionBreakpoint> existingBP = breakpoints.get(coord);
    if (existingBP == null) {
      existingBP = new BreakpointEnabling<SectionBreakpoint>(bId, type);
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

 public synchronized BreakpointEnabling<SectionBreakpoint> getReceiverBreakpoint(
      final FullSourceCoordinate section) {
    return receiverBreakpoints.computeIfAbsent(section,
        ss -> new BreakpointEnabling<SectionBreakpoint>(
            new SectionBreakpoint(false, section, BreakpointType.MSG_RECEIVER),
            SteppingType.STEP_TO_MESSAGE_RECEIVER));
  }

  public synchronized BreakpointEnabling<SectionBreakpoint> getPromiseResolverBreakpoint(
      final FullSourceCoordinate section) {
    return promiseResolverBreakpoints.computeIfAbsent(section,
        ss -> new BreakpointEnabling<>(
            new SectionBreakpoint(false, section, BreakpointType.PROMISE_RESOLVER),
            SteppingType.STEP_TO_PROMISE_RESOLVER));
  }

  public synchronized BreakpointEnabling<SectionBreakpoint> getPromiseResolutionBreakpoint(
      final FullSourceCoordinate section) {
    return promiseResolutionBreakpoints.computeIfAbsent(section,
        ss -> new BreakpointEnabling<>(
            new SectionBreakpoint(false, section, BreakpointType.PROMISE_RESOLUTION),
            SteppingType.STEP_TO_PROMISE_RESOLUTION));
  }

  public synchronized BreakpointEnabling<SectionBreakpoint> getOppositeBreakpoint(
      final FullSourceCoordinate section, final BreakpointType bType, final SteppingType sType) {
    return channelOppositeBreakpoints.computeIfAbsent(section,
        ss -> new BreakpointEnabling<>(new SectionBreakpoint(false, section, bType), sType));
  }

  public synchronized BreakpointEnabling<SectionBreakpoint> getOnExecBreakpoint(
      final FullSourceCoordinate section) {
    return onExecutionBreakpoints.computeIfAbsent(section,
        ss -> new BreakpointEnabling<>(new SectionBreakpoint(false, section, BreakpointType.ACTIVITY_ON_EXEC),
            SteppingType.STEP_INTO_ACTIVITY));
  }

  public synchronized BreakpointEnabling<SectionBreakpoint> getBeforeCommitBreakpoint(
      final FullSourceCoordinate section) {
    return beforeCommitBreakpoints.computeIfAbsent(section,
        ss -> new BreakpointEnabling<>(new SectionBreakpoint(false, section, BreakpointType.ATOMIC_BEFORE_COMMIT),
            SteppingType.STEP_TO_COMMIT));
  }

  public static AbstractBreakpointNode createPromiseResolver(final SourceSection source, final VM vm) {
    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      FullSourceCoordinate sourceCoord = SourceCoordinate.create(source);
      return BreakpointNodeGen.create(vm.getBreakpoints().getPromiseResolverBreakpoint(sourceCoord));
    } else {
      return new DisabledBreakpointNode();
    }
  }

  public static AbstractBreakpointNode createPromiseResolution(final SourceSection source, final VM vm) {
    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      FullSourceCoordinate sourceCoord = SourceCoordinate.create(source);
      return BreakpointNodeGen.create(vm.getBreakpoints().getPromiseResolutionBreakpoint(sourceCoord));
    } else {
      return new DisabledBreakpointNode();
    }
  }

  public static AbstractBreakpointNode createReceiver(final SourceSection source, final VM vm) {
    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      FullSourceCoordinate sourceCoord = SourceCoordinate.create(source);
      return BreakpointNodeGen.create(vm.getBreakpoints().getReceiverBreakpoint(sourceCoord));
    } else {
      return new DisabledBreakpointNode();
    }
  }

  public static AbstractBreakpointNode createOpposite(final SourceSection source,
      final VM vm, final BreakpointType bType, final SteppingType sType) {
    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      FullSourceCoordinate sourceCoord = SourceCoordinate.create(source);
      return BreakpointNodeGen.create(
          vm.getBreakpoints().getOppositeBreakpoint(sourceCoord, bType, sType));
    } else {
      return new DisabledBreakpointNode();
    }
  }

  public static AbstractBreakpointNode createOnExec(final SourceSection source,
      final VM vm) {
    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      FullSourceCoordinate sourceCoord = SourceCoordinate.create(source);
      return BreakpointNodeGen.create(vm.getBreakpoints().getOnExecBreakpoint(sourceCoord));
    } else {
      return new DisabledBreakpointNode();
    }
  }

  public static AbstractBreakpointNode createBeforeCommit(
      final SourceSection source, final VM vm) {
    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      FullSourceCoordinate sourceCoord = SourceCoordinate.create(source);
      return BreakpointNodeGen.create(vm.getBreakpoints().getBeforeCommitBreakpoint(sourceCoord));
    } else {
      return new DisabledBreakpointNode();
    }
  }
}
