package tools.debugger.session;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.nodes.RootNode;

import som.interpreter.LexicalScope.MixinScope;
import tools.debugger.WebDebugger;


public class Breakpoints {

  private final Map<BreakpointId, Breakpoint> knownBreakpoints;
  private final Debugger debugger;

  public Breakpoints(final Debugger debugger) {
    knownBreakpoints = new HashMap<>();
    this.debugger = debugger;
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
      return o.startLine == startLine && o.startColumn == startColumn &&
          o.charLength == charLength && o.sourceUri.equals(sourceUri);
    }
  }

  public Breakpoint getBreakpoint(final URI sourceUri, final int line) throws IOException {
    BreakpointId bId = new LineBreakpoint(sourceUri, line);
    Breakpoint bp = knownBreakpoints.get(bId);
    if (bp == null) {
      WebDebugger.log("LineBreakpoint: " + bId);
      bp = debugger.setLineBreakpoint(0, sourceUri, line, false);
      knownBreakpoints.put(bId, bp);
    }
    return bp;
  }

  public Breakpoint getBreakpoint(final URI sourceUri, final int startLine, final int startColumn, final int charLength) throws IOException {
    BreakpointId bId = new SectionBreakpoint(sourceUri, startLine, startColumn, charLength);
    Breakpoint bp = knownBreakpoints.get(bId);
    if (bp == null) {
      WebDebugger.log("SetSectionBreakpoint: " + bId);
      bp = debugger.setSourceSectionBreakpoint(0, sourceUri, startLine, startColumn, charLength, false);
      knownBreakpoints.put(bId, bp);
    }
    return bp;
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

  public BreakpointDataTrace getBreakpointDataTrace(final Set<RootNode> nodes, final URI sourceUri, final int startLine, final BreakpointId breakpointId) {
    BreakpointDataTrace breakpointTrace = null;
    RootNode rn = null;

    if (nodes != null) {
      for (RootNode rootNode : nodes) {
        int nodeStartLine = rootNode.getSourceSection().getStartLine(); //startLine of the rootNode corresponds to first line of the method
        int nodeEndLine = rootNode.getSourceSection().getEndLine();

        if (startLine > nodeStartLine && startLine < nodeEndLine) { //check if the breakpoint startLine is in the rootNode coordinates
          rn = rootNode;
          break;
        }
      }
    }

    if (rn != null) {
      MixinScope enclosingMixin = ((som.interpreter.Method) rn).getLexicalScope().getHolderScope();
      String holderClass = enclosingMixin.getMixinDefinition().getName().getString();
    //log("outer class " +enclosingMixin.getOuter().getName().getString());

      String methodName = ((som.interpreter.Method) rn).getLexicalScope().getMethod().getName().split("#")[1];
      breakpointTrace = new BreakpointDataTrace(holderClass, methodName, breakpointId);
    }

    return breakpointTrace;
  }

  /**
   * Encapsulates data related to the breakpoint.
   */
  public class BreakpointDataTrace {
    private String holderClass;
    private String methodName;
    private BreakpointId id;

    BreakpointDataTrace(final String holderClass, final String methodName,
        final BreakpointId id) {
      this.holderClass = holderClass;
      this.methodName = methodName;
      this.id = id;
    }

    public String getHolderClass() {
      return holderClass;
    }

    public String getMethodName() {
      return methodName;
    }

    public BreakpointId getId() {
      return id;
    }
  }
}
