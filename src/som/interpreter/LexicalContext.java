package som.interpreter;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;


public final class LexicalContext {
  private final FrameDescriptor frameDescriptor;
  private final LexicalContext  lexicalContext;
  @CompilationFinal private Method outerMethod;

  public LexicalContext(final FrameDescriptor frameDescriptor,
      final LexicalContext outerContext) {
    this.frameDescriptor = frameDescriptor;
    this.lexicalContext  = outerContext;
  }

  public FrameDescriptor getFrameDescriptor() {
    return frameDescriptor;
  }

  public LexicalContext getOuterContext() {
    return lexicalContext;
  }

  public Method getOuterMethod() {
    return outerMethod;
  }

  public void setOuterMethod(final Method method) {
    CompilerAsserts.neverPartOfCompilation("LexicalContext.sOM()");
    assert outerMethod == null; // should not have been set before
    outerMethod = method;
  }
}
