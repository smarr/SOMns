package tools.debugger.session;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Breakpoint.SimpleCondition;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SourceElement;
import com.oracle.truffle.api.debug.SuspendAnchor;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.source.SourceSection;

import bd.source.FullSourceCoordinate;
import bd.source.SourceCoordinate;
import som.VM;
import som.interpreter.actors.ReceivedRootNode;
import som.vm.VmSettings;
import tools.concurrency.Tags.ExpressionBreakpoint;
import tools.debugger.WebDebugger;
import tools.debugger.entities.BreakpointType;
import tools.debugger.nodes.AbstractBreakpointNode;
import tools.debugger.nodes.BreakpointNodeGen;
import tools.debugger.nodes.DisabledBreakpointNode;


public class Breakpoints {

  private final DebuggerSession debuggerSession;

  /**
   * Breakpoints directly managed by Truffle.
   */
  private final Map<BreakpointInfo, Breakpoint> truffleBreakpoints;

  /** Manually managed breakpoints. */
  private final Map<SectionBreakpoint, BreakpointEnabling> breakpoints;

  public Breakpoints(final Debugger debugger, final WebDebugger webDebugger) {
    this.truffleBreakpoints = new HashMap<>();
    this.breakpoints = new HashMap<>();
    @SuppressWarnings("unchecked")
    Class<Tag>[] tags = new Class[] {ExpressionBreakpoint.class};
    this.debuggerSession =
        debugger.startSession(webDebugger, tags, SourceElement.STATEMENT, SourceElement.ROOT);
  }

  public void prepareSteppingUntilNextRootNode(final Thread thread) {
    debuggerSession.prepareStepUntilNext(RootTag.class, SuspendAnchor.BEFORE, thread);
  }

  public void prepareSteppingAfterNextRootNode(final Thread thread) {
    debuggerSession.prepareStepUntilNext(RootTag.class, SuspendAnchor.AFTER, thread);
  }

  public synchronized void addOrUpdate(final LineBreakpoint bId) {
    Breakpoint bp = truffleBreakpoints.get(bId);
    if (bp == null) {
      WebDebugger.log("LineBreakpoint: " + bId);
      bp = Breakpoint.newBuilder(bId.getURI()).lineIs(bId.getLine()).build();
      debuggerSession.install(bp);
      truffleBreakpoints.put(bId, bp);
    }
    bp.setEnabled(bId.isEnabled());
  }

  public synchronized void addOrUpdate(final SectionBreakpoint bId) {
    SectionBreakpoint loc = new SectionBreakpoint(bId.getCoordinate(), bId.bpType);
    BreakpointEnabling existingBP = breakpoints.get(loc);
    if (existingBP == null) {
      existingBP = new BreakpointEnabling(bId);
      breakpoints.put(loc, existingBP);
    } else {
      existingBP.setEnabled(bId.isEnabled());
    }
  }

  public synchronized void addOrUpdateBeforeExpression(final SectionBreakpoint bId) {
    saveTruffleBasedBreakpoints(bId, ExpressionBreakpoint.class, SuspendAnchor.BEFORE);
  }

  public synchronized void addOrUpdateAfterExpression(final SectionBreakpoint bId) {
    saveTruffleBasedBreakpoints(bId, ExpressionBreakpoint.class, SuspendAnchor.AFTER);
  }

  public synchronized void addOrUpdateAsyncBefore(final SectionBreakpoint bId) {
    Breakpoint bp = saveTruffleBasedBreakpoints(bId, RootTag.class, SuspendAnchor.BEFORE);
    bp.setCondition(BreakWhenActivatedByAsyncMessage.INSTANCE);
  }

  public synchronized void addOrUpdateAsyncAfter(final SectionBreakpoint bId) {
    Breakpoint bp = saveTruffleBasedBreakpoints(bId, RootTag.class, SuspendAnchor.AFTER);
    bp.setCondition(BreakWhenActivatedByAsyncMessage.INSTANCE);
  }

  private Breakpoint saveTruffleBasedBreakpoints(final SectionBreakpoint bId,
      final Class<? extends Tag> tag, final SuspendAnchor anchor) {
    Breakpoint bp = truffleBreakpoints.get(bId);
    if (bp == null) {
      WebDebugger.log("SectionBreakpoint: " + bId);
      bp = Breakpoint.newBuilder(bId.getCoordinate().uri).lineIs(bId.getCoordinate().startLine)
                     .columnIs(bId.getCoordinate().startColumn)
                     .sectionLength(bId.getCoordinate().charLength)
                     .sourceElements(SourceElement.EXPRESSION)
                     .tag(tag)
                     .suspendAnchor(anchor).build();
      debuggerSession.install(bp);
      truffleBreakpoints.put(bId, bp);
    }
    bp.setEnabled(bId.isEnabled());
    return bp;
  }

  private static final class BreakWhenActivatedByAsyncMessage implements SimpleCondition {
    static BreakWhenActivatedByAsyncMessage INSTANCE = new BreakWhenActivatedByAsyncMessage();

    private BreakWhenActivatedByAsyncMessage() {}

    @Override
    public boolean evaluate() {
      RootCallTarget ct =
          (RootCallTarget) Truffle.getRuntime().getCallerFrame().getCallTarget();
      return (ct.getRootNode() instanceof ReceivedRootNode);
    }
  }

  public synchronized BreakpointEnabling getBreakpoint(
      final FullSourceCoordinate section, final BreakpointType type) {
    return breakpoints.computeIfAbsent(new SectionBreakpoint(section, type),
        ss -> new BreakpointEnabling(
            new SectionBreakpoint(false, section, type)));
  }

  public static AbstractBreakpointNode create(final SourceSection source,
      final BreakpointType type, final VM vm) {
    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      FullSourceCoordinate sourceCoord = SourceCoordinate.createFull(source);
      return BreakpointNodeGen.create(vm.getBreakpoints().getBreakpoint(sourceCoord, type));
    } else {
      return new DisabledBreakpointNode();
    }
  }
}
