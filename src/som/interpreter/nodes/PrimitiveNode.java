package som.interpreter.nodes;

import som.vm.Universe;
import som.vmobjects.SSymbol;


public abstract class PrimitiveNode extends AbstractMessageNode {

  public PrimitiveNode(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  @Override
  public ExpressionNode cloneForInlining() {
    // TODO: we need to find a better way for primitives to allow specialization during inlining.
    return this;
  }
}
