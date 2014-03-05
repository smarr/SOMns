package som.interpreter;

import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;


public class Primitive extends Invokable {

  public Primitive(final ExpressionNode primitive,
      final FrameDescriptor frameDescriptor) {
    super(null, frameDescriptor, primitive);
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
  public Invokable cloneWithNewLexicalContext(final LexicalContext outerContext) {
    FrameDescriptor inlinedFrameDescriptor = getFrameDescriptor().copy();
    LexicalContext  inlinedContext = new LexicalContext(inlinedFrameDescriptor,
        outerContext);
    ExpressionNode  inlinedBody = Inliner.doInline(getUninitializedBody(),
        inlinedContext);
    return new Primitive(inlinedBody, inlinedFrameDescriptor);
  }

  @Override
  public RootNode split() {
    return cloneWithNewLexicalContext(null);
  }

  @Override
  public ExpressionNode inline(final RootCallTarget callTarget, final SSymbol symbol) {
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
