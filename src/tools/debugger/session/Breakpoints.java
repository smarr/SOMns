package tools.debugger.session;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Breakpoint.SimpleCondition;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.DebuggerSession.SteppingLocation;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.ReceivedRootNode;
import som.interpreter.nodes.ExpressionNode;
import tools.SourceCoordinate;
import tools.SourceCoordinate.FullSourceCoordinate;
import tools.debugger.WebDebugger;


public class Breakpoints {

  private DebuggerSession debuggerSession;

  private final WebDebugger webDebugger;
  private final Map<BreakpointId, Breakpoint> knownBreakpoints;
  private final Map<FullSourceCoordinate, ReceiverBreakpoint> receiverBreakpoints;
  private Assumption receiverBreakpointVersion;

  private final Debugger debugger;

  public Breakpoints(final Debugger debugger, final WebDebugger webDebugger) {
    this.knownBreakpoints = new HashMap<>();
    this.debugger    = debugger;
    this.webDebugger = webDebugger;
    this.receiverBreakpoints = new HashMap<>();
    this.receiverBreakpointVersion = Truffle.getRuntime().createAssumption("receiverBreakpointVersion");
  }

  private void ensureOpenDebuggerSession() {
    if (debuggerSession == null) {
      debuggerSession = debugger.startSession(webDebugger);
    }
  }

  public void doSuspend(final MaterializedFrame frame, final SteppingLocation steppingLocation) {
    ensureOpenDebuggerSession();
    debuggerSession.doSuspend(frame, steppingLocation);
  }

  public void prepareSteppingUntilNextRootNode() {
    ensureOpenDebuggerSession();
    debuggerSession.prepareSteppingUntilNextRootNode();
  }

  // TODO: remove Id from name
  public abstract static class BreakpointId {
    private boolean   enabled;
    private transient Assumption unchanged;


    public BreakpointId() {
      this.unchanged = Truffle.getRuntime().createAssumption("unchanged breakpoint");
    }

    BreakpointId(final boolean enabled) {
      this();
      this.enabled = enabled;
    }

    public synchronized void setEnabled(final boolean enabled) {
      if (this.enabled != enabled) {
        this.enabled = enabled;
        this.unchanged.invalidate();
        this.unchanged = Truffle.getRuntime().createAssumption("unchanged breakpoint");
      }
    }

    public boolean isEnabled() {
      return unchanged.isValid() && enabled;
    }

    /**
     * TODO: redundant, just a work around for the DSL, which has an issue with ! currently.
     */
    public boolean isDisabled() {
      return !isEnabled();
    }

    public Assumption getAssumption() {
      return unchanged;
    }
  }

  public static class LineBreakpoint extends BreakpointId {
    private final URI sourceUri;
    private final int line;

    public LineBreakpoint(final boolean enabled, final URI sourceUri, final int line) {
      super(enabled);
      this.sourceUri = sourceUri;
      this.line = line;
    }

    LineBreakpoint() {
      super();
      this.sourceUri = null;
      this.line = 0;
    }

    public int getLine() {
      return line;
    }

    public URI getURI() {
      return sourceUri;
    }

    @Override
    public int hashCode() {
      return Objects.hash(sourceUri, line);
    }

    @Override
    public boolean equals(final Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj == null || obj.getClass() != getClass()) {
        return false;
      }
      LineBreakpoint o = (LineBreakpoint) obj;
      return o.line == line && o.sourceUri.equals(sourceUri);
    }

    @Override
    public String toString() {
      return "LineBreakpoint[" + line + ", " + sourceUri.toString() + "]";
    }
  }

  public abstract static class SectionBreakpoint extends BreakpointId {
    protected final FullSourceCoordinate coord;

    public SectionBreakpoint(final boolean enabled, final FullSourceCoordinate coord) {
      super(enabled);
      this.coord = coord;
    }

    public FullSourceCoordinate getCoordinate() {
      return coord;
    }

    @Override
    public int hashCode() {
      return coord.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
      if (obj == this) {
        return true;
      }
      if (!(obj instanceof SectionBreakpoint)) {
        return false;
      }
      SectionBreakpoint o = (SectionBreakpoint) obj;
      return o.equals(this);
    }

    @Override
    public String toString() {
      return "SectionBreakpoint: " + coord.toString();
    }
  }

  public static class SenderBreakpoint extends SectionBreakpoint {
    public SenderBreakpoint(final boolean enabled, final FullSourceCoordinate coord) {
      super(enabled, coord);
    }

    public SenderBreakpoint(final boolean enabled, final SourceSection section) {
      this(true, SourceCoordinate.create(section));
    }

    @Override
    public String toString() {
      return "SenderBreakpoint: " + coord.toString();
    }
  }

  /**
   * Breakpoint on the RootTag node of a method.
   * The method is identified by the source section info of the breakpoint.
   */
  public static class AsyncMessageBreakpoint extends SectionBreakpoint {
    public AsyncMessageBreakpoint(final boolean enabled, final FullSourceCoordinate coord) {
      super(enabled, coord);
    }
  }

  public Breakpoint getLineBreakpoint(final URI sourceUri, final int line) throws IOException {
    BreakpointId bId = new LineBreakpoint(true, sourceUri, line);
    Breakpoint bp = knownBreakpoints.get(bId);

    if (bp == null) {
      ensureOpenDebuggerSession();
      WebDebugger.log("LineBreakpoint: " + bId);
      bp = Breakpoint.newBuilder(sourceUri).
          lineIs(line).
          build();
      debuggerSession.install(bp);
      knownBreakpoints.put(bId, bp);
    }
    return bp;
  }

  public Breakpoint getBreakpointOnSender(final FullSourceCoordinate coord) throws IOException {
    BreakpointId bId = new SenderBreakpoint(true, coord);
    Breakpoint bp = knownBreakpoints.get(bId);
    if (bp == null) {
      ensureOpenDebuggerSession();
      WebDebugger.log("SetSectionBreakpoint: " + bId);
      bp = Breakpoint.newBuilder(coord.uri).
          lineIs(coord.startLine).
          columnIs(coord.startColumn).
          sectionLength(coord.charLength).
          build();
      debuggerSession.install(bp);
      knownBreakpoints.put(bId, bp);
    }
    return bp;
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

  private static final class FindRootTagNode implements NodeVisitor {

    private ExpressionNode result;

    public ExpressionNode getResult() {
      return result;
    }

    @Override
    public boolean visit(final Node node) {
      if (node instanceof ExpressionNode) {
        ExpressionNode expr = (ExpressionNode) node;
        if (expr.isMarkedAsRootExpression()) {
          result = expr;
          return false;
        }
      }
      return true;
    }

  }

  public Breakpoint getAsyncMessageRcvBreakpoint(final FullSourceCoordinate coord) throws IOException {
    BreakpointId bId = new AsyncMessageBreakpoint(true, coord);
    Breakpoint bp = knownBreakpoints.get(bId);

    if (bp == null) {
      WebDebugger.log("RootBreakpoint: " + bId);
      Source source = webDebugger.getSource(coord.uri);
      assert source != null : "TODO: handle problem somehow? defer breakpoint creation on source loading? ugh...";

      SourceSection rootSS = source.createSection(coord.startLine, coord.startColumn, coord.charLength);
      Set<RootNode> roots = webDebugger.getRootNodesBySource(source);
      for (RootNode root : roots) {
        if (rootSS.equals(root.getSourceSection())) {
          FindRootTagNode finder = new FindRootTagNode();
          root.accept(finder);
          ExpressionNode rootExpression = finder.getResult();
          assert rootExpression.getSourceSection() != null;

          ensureOpenDebuggerSession();
          bp = Breakpoint.newBuilder(rootExpression.getSourceSection()).
              build();
          debuggerSession.install(bp);
          bp.setCondition(BreakWhenActivatedByAsyncMessage.INSTANCE);
          knownBreakpoints.put(bId, bp);
        }
      }
    }
    return bp;
  }

  public synchronized void addReceiverBreakpoint(final FullSourceCoordinate coord) {
    ReceiverBreakpoint bId = new ReceiverBreakpoint(true, coord);
    ReceiverBreakpoint existingBP = receiverBreakpoints.get(bId);
    if (existingBP == null) {
      receiverBreakpoints.put(coord, bId);
    }
    else {
      existingBP.setEnabled(true);
    }


    receiverBreakpointVersion.invalidate();
    receiverBreakpointVersion = Truffle.getRuntime().createAssumption();
  }

  public static class ReceiverBreakpoint extends SectionBreakpoint {
    public ReceiverBreakpoint(final boolean enabled, final FullSourceCoordinate coord) {
      super(enabled, coord);
    }

    public ReceiverBreakpoint(final boolean enabled, final SourceSection section) {
      this(enabled, SourceCoordinate.create(section));
    }

    @Override
    public String toString() {
      return "ReceiverBreakpoint: " + coord.toString();
    }
  }

  public synchronized ReceiverBreakpoint getReceiverBreakpoint(
      final FullSourceCoordinate section) {
    return receiverBreakpoints.computeIfAbsent(section,
        ss -> new ReceiverBreakpoint(false, section));
  }
}
