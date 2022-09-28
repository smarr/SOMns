package som.interpreter;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;

import som.compiler.MethodBuilder;
import som.interpreter.actors.ResolvePromiseNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.SOMNode;
import som.primitives.ObjectPrims.HaltPrim;
import som.vmobjects.SInvokable;


public final class Primitive extends Invokable {

  public Primitive(final String name, final ExpressionNode primitive,
      final FrameDescriptor frameDescriptor,
      final ExpressionNode uninitialized, final boolean isAtomic,
      final SomLanguage lang) {
    super(name, null, frameDescriptor, primitive, uninitialized, isAtomic, lang);
  }

  @Override
  public ExpressionNode inline(final MethodBuilder builder, final SInvokable outer) {
    // Note for completeness: for primitives, we use eager specialization,
    // which is essentially much simpler inlining
    throw new UnsupportedOperationException(
        "Primitives are currently not directly inlined. Only block methods are.");
  }

  @Override
  public Node deepCopy() {
    assert getFrameDescriptor().getSize() == 0;
    return new Primitive(name, NodeUtil.cloneNode(uninitializedBody),
        getFrameDescriptor(), uninitializedBody, isAtomic,
        SomLanguage.getLanguage(this));
  }

  @Override
  public Invokable createAtomic() {
    assert !isAtomic : "We should only ask non-atomic invokables for their atomic version";
    ExpressionNode atomic = NodeUtil.cloneNode(uninitializedBody);
    ExpressionNode uninitAtomic = NodeUtil.cloneNode(atomic);

    return new Primitive(name, atomic, getFrameDescriptor(), uninitAtomic, true,
        SomLanguage.getLanguage(this));
  }

  @Override
  public String toString() {
    ExpressionNode n = SOMNode.unwrapIfNecessary(expressionOrSequence);
    String nodeType = n.getClass().getSimpleName();
    if (n != expressionOrSequence) {
      nodeType += " (wrapped)"; // indicate that it is wrapped
    }
    return nodeType + "\t@" + Integer.toHexString(hashCode());
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
    return expressionOrSequence instanceof HaltPrim
        || expressionOrSequence instanceof ResolvePromiseNode;
  }

  private static Method getNextMethodOnStack() {
    return Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Method>() {

      @Override
      public Method visitFrame(final FrameInstance frameInstance) {
        RootCallTarget ct = (RootCallTarget) frameInstance.getCallTarget();
        Invokable m = (Invokable) ct.getRootNode();

        // the caller is a primitive, that doesn't help, we need to skip it and
        // find a proper method
        if (m instanceof Primitive) {
          return null;
        } else {
          return (Method) m;
        }
      }
    }, 1); // skip the first frame on stack
  }

  public static void propagateLoopCount(final long count) {
    CompilerAsserts.neverPartOfCompilation("Primitive.pLC(.)");

    Invokable m = getNextMethodOnStack();

    if (m != null && !(m instanceof Primitive)) {
      m.propagateLoopCountThroughoutMethodScope(count);
    }
  }
}
