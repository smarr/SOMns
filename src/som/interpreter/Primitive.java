package som.interpreter;

import java.util.Iterator;

import som.interpreter.nodes.ExpressionNode;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.nodes.RootNode;


public final class Primitive extends Invokable {

  public Primitive(final ExpressionNode primitiveEnforced,
      final ExpressionNode primitiveUnenforced,
      final FrameDescriptor frameDescriptor) {
    super(null, frameDescriptor, primitiveEnforced, primitiveUnenforced);
  }

  @Override
  public AbstractInvokable cloneWithNewLexicalContext(final LexicalContext outerContext) {
    FrameDescriptor inlinedFrameDescriptor = getFrameDescriptor().copy();
    LexicalContext  inlinedContext = new LexicalContext(inlinedFrameDescriptor,
        outerContext);
    ExpressionNode  inlinedEnforcedBody = Inliner.doInline(
        uninitializedEnforcedBody, inlinedContext);
    ExpressionNode  inlinedUnenforcedBody = Inliner.doInline(
        uninitializedUnenforcedBody, inlinedContext);
    return new Primitive(inlinedEnforcedBody, inlinedUnenforcedBody,
        inlinedFrameDescriptor);
  }

  @Override
  public RootNode split() {
    return cloneWithNewLexicalContext(null);
  }

  @Override
  public boolean isBlock() {
    return false;
  }

  @Override
  public String toString() {
    return "Primitive " + unenforcedBody.getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
  }

  @Override
  public void propagateLoopCountThroughoutLexicalScope(final long count) {
    CompilerAsserts.neverPartOfCompilation();
    // we need to skip the primitive and get to the method that called the primitive
    Iterable<FrameInstance> stack = Truffle.getRuntime().getStackTrace();
    Iterator<FrameInstance> i = stack.iterator();

    i.next();  // current frame
    FrameInstance next2 = i.next();  // caller frame

    RootCallTarget ct = (RootCallTarget) next2.getCallTarget();  // caller method
    Method m = (Method) ct.getRootNode();
    m.propagateLoopCountThroughoutLexicalScope(count);
  }

  @Override
  public void setOuterContextMethod(final AbstractInvokable method) {
    CompilerAsserts.neverPartOfCompilation();
    throw new UnsupportedOperationException("Only supported on methods");
  }
}
