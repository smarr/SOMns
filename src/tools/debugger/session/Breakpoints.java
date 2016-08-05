package tools.debugger.session;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Breakpoint.SimpleCondition;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.ReceivedRootNode;
import som.interpreter.nodes.ExpressionNode;
import tools.debugger.WebDebugger;


public class Breakpoints {

  private final WebDebugger webDebugger;
  private final Map<BreakpointId, Breakpoint> knownBreakpoints;
  private final List<SectionBreakpoint> receiverBreakpoints;
  private final Debugger debugger;

  public Breakpoints(final Debugger debugger, final WebDebugger webDebugger) {
    knownBreakpoints = new HashMap<>();
    this.debugger    = debugger;
    this.webDebugger = webDebugger;
    this.receiverBreakpoints = new ArrayList<>();
  }

  public abstract static class BreakpointId {

  }

  static class LineBreakpoint extends BreakpointId {
    private final URI sourceUri;
    private final int line;

    LineBreakpoint(final URI sourceUri, final int line) {
      this.sourceUri = sourceUri;
      this.line = line;
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
  }

  public static class SectionBreakpoint extends BreakpointId {
    private final URI sourceUri;
    private final int startLine;
    private final int startColumn;
    private final int charLength;

    public SectionBreakpoint(final URI sourceUri, final int startLine,
        final int startColumn, final int charLength) {
      this.sourceUri = sourceUri;
      this.startLine = startLine;
      this.startColumn = startColumn;
      this.charLength  = charLength;
    }

    @Override
    public int hashCode() {
      return Objects.hash(sourceUri, startLine, startColumn, charLength);
    }

    @Override
    public boolean equals(final Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj == null || obj.getClass() != getClass()) {
        return false;
      }
      SectionBreakpoint o = (SectionBreakpoint) obj;
      return o.startLine == startLine && o.startColumn == startColumn
          && o.charLength == charLength && o.sourceUri.equals(sourceUri);
    }

    @Override
    public String toString() {
      return "SectionBreakpoint: startLine " + startLine + " startColumn "
          + startColumn + " charLength " + charLength + " sourceURi " + sourceUri;
    }
  }

  /**
   * Breakpoint on the RootTag node of a method.
   * The method is identified by the source section info of the breakpoint.
   */
  public static class RootBreakpoint extends SectionBreakpoint {
    public RootBreakpoint(final URI sourceUri, final int startLine,
        final int startColumn, final int charLength) {
      super(sourceUri, startLine, startColumn, charLength);
    }
  }

  public Breakpoint getLineBreakpoint(final URI sourceUri, final int line) throws IOException {
    BreakpointId bId = new LineBreakpoint(sourceUri, line);
    Breakpoint bp = knownBreakpoints.get(bId);

    if (bp == null) {
      WebDebugger.log("LineBreakpoint: " + bId);
      bp = debugger.setLineBreakpoint(0, sourceUri, line, false);
      knownBreakpoints.put(bId, bp);
    }
    return bp;
  }

  public Breakpoint getBreakpointOnSender(final URI sourceUri, final int startLine, final int startColumn, final int charLength) throws IOException {
    BreakpointId bId = new SectionBreakpoint(sourceUri, startLine, startColumn, charLength);
    Breakpoint bp = knownBreakpoints.get(bId);
    if (bp == null) {
      WebDebugger.log("SetSectionBreakpoint: " + bId);
      bp = debugger.setSourceSectionBreakpoint(0, sourceUri, startLine, startColumn, charLength, false);
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

  public Breakpoint getAsyncMessageRcvBreakpoint(final URI sourceUri,
      final int startLine, final int startColumn, final int charLength) throws IOException {
    BreakpointId bId = new RootBreakpoint(sourceUri, startLine, startColumn, charLength);
    Breakpoint bp = knownBreakpoints.get(bId);

    if (bp == null) {
      WebDebugger.log("RootBreakpoint: " + bId);
      Source source = webDebugger.getSource(sourceUri);
      assert source != null : "TODO: handle problem somehow? defer breakpoint creation on source loading? ugh...";

      SourceSection rootSS = source.createSection(null, startLine, startColumn, charLength);
      Set<RootNode> roots = webDebugger.getRootNodesBySource(source);
      for (RootNode root : roots) {
        if (rootSS.equals(root.getSourceSection())) {
          FindRootTagNode finder = new FindRootTagNode();
          root.accept(finder);
          ExpressionNode rootExpression = finder.getResult();
          assert rootExpression.getSourceSection() != null;

          bp = debugger.setSourceSectionBreakpoint(0, rootExpression.getSourceSection(), false);
          bp.setCondition(BreakWhenActivatedByAsyncMessage.INSTANCE);
          knownBreakpoints.put(bId, bp);
        }
      }
    }
    return bp;
  }

  public void saveReceiverBreakpoint(final URI sourceUri, final int startLine,
      final int startColumn, final int charLength) {
    SectionBreakpoint bId = new SectionBreakpoint(sourceUri, startLine, startColumn, charLength);
    if (!receiverBreakpoints.contains(bId)) {
      this.receiverBreakpoints.add(bId);
    }
  }

  public boolean isBreakpointed(final SourceSection source) {
    if (!receiverBreakpoints.isEmpty()) {
      SectionBreakpoint checkBreakpoint = new SectionBreakpoint(source.getSource().getURI(),
          source.getStartLine(), source.getStartColumn(),
          source.getCharLength());

      if (receiverBreakpoints.contains(checkBreakpoint)) {
        return true;
      }
    }
    return false;
  }

  public BreakpointId getBreakpointId(final URI sourceUri,
      final int startLine, final int startColumn, final int charLength) {
    Set<BreakpointId> ids = knownBreakpoints.keySet();

    for (BreakpointId breakpointId : ids) {
      if (breakpointId instanceof SectionBreakpoint) {
        BreakpointId bId = new SectionBreakpoint(sourceUri, startLine, startColumn, charLength);
        SectionBreakpoint sb = (SectionBreakpoint) breakpointId;
        if (sb.equals(bId)) {
          return sb;
        }

      } else if (breakpointId instanceof LineBreakpoint) {
        BreakpointId bId = new LineBreakpoint(sourceUri, startLine);
        LineBreakpoint lb = (LineBreakpoint) breakpointId;
        if (lb.equals(bId)) {
          return lb;
        }
      }
    }

    return null;
  }
}
