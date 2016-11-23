package tools.debugger.frontend;

import java.util.List;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;

import som.compiler.Variable.Argument;
import som.interpreter.LexicalScope.MethodScope;


public class RuntimeScope {
  private final MaterializedFrame frame;
  private final MethodScope lexicalScope;

  public RuntimeScope(final MaterializedFrame frame, final MethodScope lexcialScope) {
    this.frame = frame;
    this.lexicalScope = lexcialScope;
    assert frame.getFrameDescriptor() == lexcialScope.getFrameDescriptor();
  }

  public Argument[] getArguments() {
    return lexicalScope.getMethod().getArguments();
  }

  public Object getArgument(final int idx) {
    return frame.getArguments()[idx];
  }

  public List<? extends FrameSlot> getLocals() {
    return lexicalScope.getFrameDescriptor().getSlots();
  }

  public Object getLocal(final FrameSlot slot) {
    return frame.getValue(slot);
  }
}
