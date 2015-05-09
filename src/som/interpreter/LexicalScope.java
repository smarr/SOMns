package som.interpreter;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;


public final class LexicalScope {
  private final FrameDescriptor frameDescriptor;
  private final LexicalScope    outerScope;
  @CompilationFinal private Method method;

  public LexicalScope(final FrameDescriptor frameDescriptor,
      final LexicalScope outerScope) {
    this.frameDescriptor = frameDescriptor;
    this.outerScope      = outerScope;
  }

  public FrameDescriptor getFrameDescriptor() {
    return frameDescriptor;
  }

  public LexicalScope getOuterScopeOrNull() {
    return outerScope;
  }

  public LexicalScope getOuterScope() {
    assert outerScope != null;
    return outerScope;
  }

  public void propagateLoopCountThroughoutLexicalScope(final long count) {
    if (outerScope != null) {
      outerScope.method.propagateLoopCountThroughoutLexicalScope(count);
    }
  }

  public Method getMethod() {
    return method;
  }

  public void setMethod(final Method method) {
    CompilerAsserts.neverPartOfCompilation("LexicalContext.sOM()");
    // might be reset when doing inlining/embedded, but should always
    // refer to the same method
    assert this.method == null ||
        this.method.getSourceSection() == method.getSourceSection();
    this.method = method;
  }

  @Override
  public String toString() {
    return "LexScp[" + frameDescriptor.toString() + "]";
  }
}
