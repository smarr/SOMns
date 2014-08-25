package som.interpreter;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.enforced.EnforcedPrim;
import som.primitives.EmptyPrim;
import som.vmobjects.SInvokable.SPrimitive;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.RootNode;


public final class Primitive extends Invokable {

  public Primitive(final ExpressionNode primitive,
      final FrameDescriptor frameDescriptor,
      final boolean executesEnforced) {
    super(null, frameDescriptor, primitive, executesEnforced);
  }

  @Override
  public AbstractInvokable cloneWithNewLexicalContext(final LexicalContext outerContext) {
    FrameDescriptor inlinedFrameDescriptor = getFrameDescriptor().copy();
    LexicalContext  inlinedContext = new LexicalContext(inlinedFrameDescriptor,
        outerContext);
    ExpressionNode  inlinedBody = Inliner.doInline(
        uninitializedBody, inlinedContext);
    return new Primitive(inlinedBody,
        inlinedFrameDescriptor, executesEnforced);
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
  public boolean isEmptyPrimitive() {
    return uninitializedBody instanceof EmptyPrim;
  }

  @Override
  public String toString() {
    return "Primitive " + body.getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
  }

  @Override
  public void propagateLoopCountThroughoutLexicalScope(final long count) {

    propagateLoopCount(count);
  }

  @Override
  public void setOuterContextMethod(final AbstractInvokable method) {
    CompilerAsserts.neverPartOfCompilation("Primitive.setOuterContextMethod()");
    throw new UnsupportedOperationException("Only supported on methods");
  }

  private static Method getNextMethodOnStack() {
    return Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Method>() {
        @Override
        public Method visitFrame(final FrameInstance frameInstance) {
          RootCallTarget ct = (RootCallTarget) frameInstance.getCallTarget();
          AbstractInvokable m = (AbstractInvokable) ct.getRootNode();
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
    AbstractInvokable m = (AbstractInvokable) ct.getRootNode();

    if (m instanceof Primitive) {
      // the caller is a primitive, that doesn't help, we need to skip it and
      // find a proper method
      m = getNextMethodOnStack();
    }

    if (m != null && !(m instanceof Primitive)) {
      m.propagateLoopCountThroughoutLexicalScope(count);
    }
  }

  public void setPrimitive(final SPrimitive prim) {
    ((EnforcedPrim) uninitializedBody).setPrimitive(prim);
    ((EnforcedPrim) body).setPrimitive(prim);
  }
}
