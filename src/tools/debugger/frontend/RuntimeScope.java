package tools.debugger.frontend;

import com.oracle.truffle.api.frame.Frame;

import som.compiler.Variable;
import som.interpreter.LexicalScope.MethodScope;
import com.oracle.truffle.api.frame.Frame;


public class RuntimeScope {
  private final Frame       frame;
  private final MethodScope lexicalScope;

  public RuntimeScope(final Frame frame, final MethodScope lexicalScope) {
    this.frame = frame;
    this.lexicalScope = lexicalScope;
    assert frame.getFrameDescriptor() == lexicalScope.getFrameDescriptor();
  }

  public Variable[] getVariables() {
    return lexicalScope.getVariables();
  }

  public Object read(final Variable var) {
    return var.read(frame);
  }
}
