package som.interpreter.nodes;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChild(value = "exp", type = ExpressionNode.class)
public abstract class VariableWriteNode extends VariableNode {

  public VariableWriteNode(final FrameSlot slot, final int contextLevel) {
    super(slot, contextLevel);
  }

  public VariableWriteNode(final VariableWriteNode node) {
    this(node.slot, node.contextLevel);
  }

  @Specialization(rewriteOn = FrameSlotTypeException.class)
  public int write(final VirtualFrame frame, final int expValue) throws FrameSlotTypeException {
    MaterializedFrame ctx = determineContext(frame.materialize());
    ctx.setInt(slot, expValue);
    return expValue;
  }

  @Specialization(rewriteOn = FrameSlotTypeException.class)
  public double write(final VirtualFrame frame, final double expValue) throws FrameSlotTypeException {
    MaterializedFrame ctx = determineContext(frame.materialize());
    ctx.setDouble(slot, expValue);
    return expValue;
  }

  @Specialization
  public Object writeGeneric(final VirtualFrame frame, final Object expValue) {
    MaterializedFrame ctx = determineContext(frame.materialize());
    ctx.setObject(slot, expValue);
    return expValue;
  }


//    @Override
//    public SAbstractObject executeGeneric(final VirtualFrame frame) {
//      SAbstractObject result = (SAbstractObject) exp.executeGeneric(frame); // TODO: Work out whether there is another way than this cast!
//      MaterializedFrame ctx = determineContext(frame.materialize());
//
//      ctx.setObject(slot, result);
//
//      return result;
//    }
}
