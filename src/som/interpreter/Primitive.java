package som.interpreter;

import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.nodes.ExpressionNode;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.Node;


public final class Primitive extends Invokable {

  public Primitive(final ExpressionNode primitive,
      final FrameDescriptor frameDescriptor,
      final ExpressionNode uninitialized) {
    super(null, frameDescriptor, primitive, uninitialized);
  }

  @Override
  public Invokable cloneWithNewLexicalContext(final MethodScope outerContext) {
    assert outerContext == null;
    FrameDescriptor inlinedFrameDescriptor = getFrameDescriptor().copy();
    MethodScope  inlinedContext = new MethodScope(inlinedFrameDescriptor,
        outerContext, null /* since we got an outer method scope, there won't be a direct class scope*/);
    ExpressionNode  inlinedBody = SplitterForLexicallyEmbeddedCode.doInline(uninitializedBody,
        inlinedContext);
    return new Primitive(inlinedBody, inlinedFrameDescriptor, uninitializedBody);
  }

  @Override
  public Node deepCopy() {
    return cloneWithNewLexicalContext(null);
  }

  @Override
  public String toString() {
    return "Primitive " + expressionOrSequence.getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
  }

  @Override
  public void propagateLoopCountThroughoutMethodScope(final long count) {
    propagateLoopCount(count);
  }

  /**
   * Primitive operations are not instrumentable. They are not user-level
   * behavior, and thus, are supposed to remain opaque.
   */
  @Override
  protected boolean isInstrumentable() {
    return false;
  }

  private static Method getNextMethodOnStack() {
    return Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Method>() {
        @Override
        public Method visitFrame(final FrameInstance frameInstance) {
          RootCallTarget ct = (RootCallTarget) frameInstance.getCallTarget();
          Invokable m = (Invokable) ct.getRootNode();
          if (m instanceof Primitive) {
            return null;
          } else {
            return (Method) m;
          }
        }
      });
  }

  public static void propagateLoopCount(final long count) {
    CompilerAsserts.neverPartOfCompilation("Primitive.pLC(.)");

    // we need to skip the primitive and get to the method that called the primitive
    FrameInstance caller = Truffle.getRuntime().getCallerFrame();

    RootCallTarget ct = (RootCallTarget) caller.getCallTarget();  // caller method
    Invokable m = (Invokable) ct.getRootNode();

    if (m instanceof Primitive) {
      // the caller is a primitive, that doesn't help, we need to skip it and
      // find a proper method
      m = getNextMethodOnStack();
    }

    if (m != null && !(m instanceof Primitive)) {
      m.propagateLoopCountThroughoutMethodScope(count);
    }
  }
}
