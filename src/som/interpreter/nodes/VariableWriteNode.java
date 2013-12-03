package som.interpreter.nodes;

import som.vmobjects.SAbstractObject;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;

//@NodeChild(value = "exp", type = ExpressionNode.class)
//abstract
public class VariableWriteNode extends VariableNode {
  @Child private ExpressionNode exp;

  public VariableWriteNode(final FrameSlot slot, final int contextLevel, final ExpressionNode exp) {
    super(slot, contextLevel);
    this.exp = adoptChild(exp);
  }

  public VariableWriteNode(final VariableWriteNode node) {
    this(node.slot, node.contextLevel, node.exp);
  }

//  @Specialization(rewriteOn = FrameSlotTypeException.class)
//  public int write(final VirtualFrame frame, int expValue) throws FrameSlotTypeException {
//    MaterializedFrame ctx = determineContext(frame.materialize());
//    ctx.setInt(slot, expValue);
//    return expValue;
//  }
//
//  @Specialization(rewriteOn = FrameSlotTypeException.class)
//  public double write(final VirtualFrame frame, double expValue) throws FrameSlotTypeException {
//    MaterializedFrame ctx = determineContext(frame.materialize());
//    ctx.setDouble(slot, expValue);
//    return expValue;
//  }
//
//  @Specialization(rewriteOn = FrameSlotTypeException.class)
//  public boolean write(final VirtualFrame frame, boolean expValue) throws FrameSlotTypeException {
//    MaterializedFrame ctx = determineContext(frame.materialize());
//    ctx.setBoolean(slot, expValue);
//    return expValue;
//  }
//
//  @Specialization
//  public Object writeGeneric(final VirtualFrame frame, Object expValue) {
//    MaterializedFrame ctx = determineContext(frame.materialize());
//    ctx.setObject(slot, expValue);
//    return expValue;
//  }

//  @Override
//  public Object executeGeneric(final VirtualFrame frame) {
//    Object expValue = ((VariableWriteBaseNode) this).exp.executeGeneric(frame);
//
//    return TYPES.expectSOMObject(super.executeAndSpecialize0(0, frameValue, expValue, "Uninitialized monomorphic"));
//
//  }

    @Override
    public SAbstractObject executeGeneric(final VirtualFrame frame) {
      SAbstractObject result = (SAbstractObject) exp.executeGeneric(frame); // TODO: Work out whether there is another way than this cast!
      MaterializedFrame ctx = determineContext(frame.materialize());

      ctx.setObject(slot, result);

      return result;
    }
}
