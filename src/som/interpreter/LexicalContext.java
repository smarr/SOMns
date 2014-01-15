package som.interpreter;

import com.oracle.truffle.api.frame.FrameDescriptor;


public final class LexicalContext {
  private final FrameDescriptor frameDescriptor;
  private final LexicalContext  lexicalContext;

  public LexicalContext(final FrameDescriptor frameDescriptor, final LexicalContext outerContext) {
    this.frameDescriptor = frameDescriptor;
    this.lexicalContext  = outerContext;
  }

  public FrameDescriptor getFrameDescriptor() {
    return frameDescriptor;
  }

  public LexicalContext getOuterContext() {
    return lexicalContext;
  }
}
