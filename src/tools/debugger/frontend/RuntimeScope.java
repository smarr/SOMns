package tools.debugger.frontend;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;

import som.compiler.Variable;
import som.interpreter.LexicalScope.MethodScope;


public class RuntimeScope {
  private final MaterializedFrame frame;
  private final MethodScope lexicalScope;

  public RuntimeScope(final MaterializedFrame frame, final MethodScope lexcialScope) {
    this.frame = frame;
    this.lexicalScope = lexcialScope;
    assert frame.getFrameDescriptor() == lexcialScope.getFrameDescriptor();
  }

  public Variable[] getVariables() {
    return lexicalScope.getVariables();
  }

  public Object getArgument(final int idx) {
    return frame.getArguments()[idx];
  }

  public Object getLocal(final FrameSlot slot) {
    return frame.getValue(slot);
  }
}
