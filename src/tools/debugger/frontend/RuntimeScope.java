package tools.debugger.frontend;

import com.oracle.truffle.api.frame.Frame;

import som.compiler.Variable;
import som.interpreter.LexicalScope.MethodScope;
import com.oracle.truffle.api.frame.Frame;


public class RuntimeScope {
  private final Frame       frame;
  private final MethodScope lexicalScope;

  public RuntimeScope(final Frame frame, final MethodScope lexcialScope) {
    this.frame = frame;
    this.lexicalScope = lexcialScope;
    assert frame.getFrameDescriptor() == lexcialScope.getFrameDescriptor();
  }

  public Variable[] getVariables() {
    return lexicalScope.getVariables();
  }

  public Object read(final Variable var) {
    return var.read(frame);
  }
}
