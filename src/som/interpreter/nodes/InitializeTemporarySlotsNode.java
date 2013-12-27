package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;


public final class InitializeTemporarySlotsNode extends ExpressionNode {
  @Children protected WriteConstantToField[] tempSlotInitializers;
  @Child    protected ExpressionNode methodBody;

  public InitializeTemporarySlotsNode(final WriteConstantToField[] initializers,
      final ExpressionNode methodBody) {
    tempSlotInitializers = adoptChildren(initializers);
    this.methodBody      = adoptChild(methodBody);
  }

  @ExplodeLoop
  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    for (int i = 0; i < tempSlotInitializers.length; i++) {
      tempSlotInitializers[i].executeGeneric(frame);
    }
    return methodBody.executeGeneric(frame);
  }
}
