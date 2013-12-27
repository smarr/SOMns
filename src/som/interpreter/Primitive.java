package som.interpreter;

import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeUtil;


public class Primitive extends Invokable {

  public Primitive(final ExpressionNode primitive,
      final int numArguments,
      final FrameDescriptor frameDescriptor) {
    super(primitive, numArguments, frameDescriptor);
  }

  @Override
  public Object execute(final VirtualFrame frame) {
    return expressionOrSequence.executeGeneric(frame);
  }

  @Override
  public boolean isAlwaysToBeInlined() {
    return true;
  }

  @Override
  public int getNumberOfUpvalues() {
    return 0; // primtives do not have a body, and thus, no upvalues
  }

  @Override
  public ExpressionNode inline(final CallTarget inlinableCallTarget, final SSymbol selector) {
    // for primitives, we assume that they are wrapped in a proper *SendNode
    // And, that inlining is realized so that the monomorphic/PIC check is
    // done correctly, and afterwards, the `executeEvaluated(..)` method
    // gets called on the inlined node.
    return NodeUtil.cloneNode(getUninitializedBody());
  }

  @Override
  public String toString() {
    return "Primitive " + expressionOrSequence.getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
  }

}
