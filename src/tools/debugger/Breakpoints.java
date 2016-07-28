package tools.debugger;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;


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

  public Map<BreakpointId, Breakpoint> getKnownBreakpoints() {
    return knownBreakpoints;
  }
}
