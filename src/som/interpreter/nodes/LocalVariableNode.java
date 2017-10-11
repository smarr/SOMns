package som.interpreter.nodes;

import java.util.List;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import som.compiler.Variable.Local;
import som.interpreter.InliningVisitor;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.interpreter.nodes.superinstructions.AssignProductToVariableNode;
import som.interpreter.nodes.superinstructions.AssignSubtractionResultNode;
import som.interpreter.nodes.superinstructions.IncrementOperationNode;
import som.vm.VmSettings;
import som.vm.constants.Nil;
import tools.debugger.Tags.LocalVariableTag;
import tools.dym.Tags.LocalVarRead;
import tools.dym.Tags.LocalVarWrite;

public abstract class LocalVariableNode extends ExprWithTagsNode {
  protected final FrameSlot slot;
  protected final Local     var;

  protected LocalVariableNode(final Local var) {
    this.slot = var.getSlot();
    this.var = var;
  }

  public Local getVar() {
    return this.var;
  }

  @Override
  protected boolean isTaggedWith(final Class<?> tag) {
    if (tag == LocalVariableTag.class) {
      return true;
    } else {
      return super.isTaggedWith(tag);
    }
  }

  public abstract static class LocalVariableReadNode extends LocalVariableNode {

    public LocalVariableReadNode(final Local variable) {
      super(variable);
    }

    public LocalVariableReadNode(final LocalVariableReadNode node) {
      this(node.var);
    }

    @Specialization(guards = "isUninitialized(frame)")
    public final Object doNil(final VirtualFrame frame) {
      return Nil.nilObject;
    }

    protected boolean isBoolean(final VirtualFrame frame) {
      return frame.isBoolean(slot);
    }

    protected boolean isLong(final VirtualFrame frame) {
      return frame.isLong(slot);
    }

    protected boolean isDouble(final VirtualFrame frame) {
      return frame.isDouble(slot);
    }

    protected boolean isObject(final VirtualFrame frame) {
      return frame.isObject(slot);
    }

    @Specialization(guards = {"isBoolean(frame)"}, rewriteOn = {FrameSlotTypeException.class})
    public final boolean doBoolean(final VirtualFrame frame) throws FrameSlotTypeException {
      return frame.getBoolean(slot);
    }

    @Specialization(guards = {"isLong(frame)"}, rewriteOn = {FrameSlotTypeException.class})
    public final long doLong(final VirtualFrame frame) throws FrameSlotTypeException {
      return frame.getLong(slot);
    }

    @Specialization(guards = {"isDouble(frame)"}, rewriteOn = {FrameSlotTypeException.class})
    public final double doDouble(final VirtualFrame frame) throws FrameSlotTypeException {
      return frame.getDouble(slot);
    }

    @Specialization(guards = {"isObject(frame)"},
        replaces = {"doBoolean", "doLong", "doDouble"},
        rewriteOn = {FrameSlotTypeException.class})
    public final Object doObject(final VirtualFrame frame) throws FrameSlotTypeException {
      return frame.getObject(slot);
    }

    protected final boolean isUninitialized(final VirtualFrame frame) {
      return slot.getKind() == FrameSlotKind.Illegal;
    }

    @Override
    protected final boolean isTaggedWith(final Class<?> tag) {
      if (tag == LocalVarRead.class) {
        return true;
      } else {
        return super.isTaggedWith(tag);
      }
    }

    @Override
    public String toString() {
      return this.getClass().getSimpleName() + "[" + var.name + "]";
    }

    @Override
    public void replaceAfterScopeChange(final InliningVisitor inliner) {
      inliner.updateRead(var, this, 0);
    }
  }

  @ImportStatic({
          IncrementOperationNode.class,
          AssignSubtractionResultNode.class,
          AssignProductToVariableNode.class,
          VmSettings.class})
  @NodeChild(value = "exp", type = ExpressionNode.class)
  public abstract static class LocalVariableWriteNode extends LocalVariableNode {

    public LocalVariableWriteNode(final Local variable) {
      super(variable);
    }

    public LocalVariableWriteNode(final LocalVariableWriteNode node) {
      super(node.var);
    }

    public abstract ExpressionNode getExp();

    @Specialization(guards = "isBoolKind(expValue)")
    public final boolean writeBoolean(final VirtualFrame frame, final boolean expValue) {
      frame.setBoolean(slot, expValue);
      return expValue;
    }

    /** Check for ``AssignSubtractionResultNode`` superinstruction and replcae where applicable */
    @Specialization(guards = {"SUPERINSTRUCTIONS", "isAssignSubtract", "isDoubleKind(expValue)"})
    public final double writeDoubleAndReplaceWithAssignSubtract(final VirtualFrame frame,
                                                        final double expValue,
                                                        final @Cached("isAssignSubtractionResultOperation(getExp())")
                                                                   boolean isAssignSubtract) {
      frame.setDouble(slot, expValue);
      AssignSubtractionResultNode.replaceNode(this);
      return expValue;
    }

    /** Check for ``IncrementOperationNode`` superinstruction and replcae where applicable */
    @Specialization(guards = {"SUPERINSTRUCTIONS", "isIncrement", "isLongKind(expValue)"})
    public final long writeLongAndReplaceWithIncrement(final VirtualFrame frame,
                         final long expValue,
                         final @Cached("isIncrementOperation(getExp(), var)") boolean isIncrement) {
      frame.setLong(slot, expValue);
      IncrementOperationNode.replaceNode(this);
      return expValue;
    }

    /** Check for ``WhileSmallerEqualThanArgumentNode`` superinstruction and replace where applicable */
    @Specialization(guards = {"SUPERINSTRUCTIONS", "isAssign", "isDoubleKind(expValue)"})
    public final double writeDoubleAndReplaceWithAssignProduct(final VirtualFrame frame,
                                         final double expValue,
                                         final @Cached("isAssignProductOperation(getExp())") boolean isAssign) {
      frame.setDouble(slot, expValue);
      AssignProductToVariableNode.replaceNode(this);
      return expValue;
    }

    @Specialization(guards = "isLongKind(expValue)")
    public final long writeLong(final VirtualFrame frame, final long expValue) {
      frame.setLong(slot, expValue);
      return expValue;
    }

    @Specialization(guards = "isDoubleKind(expValue)")
    public final double writeDouble(final VirtualFrame frame, final double expValue) {
      frame.setDouble(slot, expValue);
      return expValue;
    }

    @Specialization(replaces = {"writeBoolean", "writeLong", "writeDouble"})
    public final Object writeGeneric(final VirtualFrame frame, final Object expValue) {
      slot.setKind(FrameSlotKind.Object);
      frame.setObject(slot, expValue);
      return expValue;
    }

    // uses expValue to make sure guard is not converted to assertion
    protected final boolean isBoolKind(final boolean expValue) {
      if (slot.getKind() == FrameSlotKind.Boolean) {
        return true;
      }
      if (slot.getKind() == FrameSlotKind.Illegal) {
        slot.setKind(FrameSlotKind.Boolean);
        return true;
      }
      return false;
    }

    // uses expValue to make sure guard is not converted to assertion
    protected final boolean isLongKind(final long expValue) {
      if (slot.getKind() == FrameSlotKind.Long) {
        return true;
      }
      if (slot.getKind() == FrameSlotKind.Illegal) {
        slot.setKind(FrameSlotKind.Long);
        return true;
      }
      return false;
    }

    // uses expValue to make sure guard is not converted to assertion
    protected final boolean isDoubleKind(final double expValue) {
      if (slot.getKind() == FrameSlotKind.Double) {
        return true;
      }
      if (slot.getKind() == FrameSlotKind.Illegal) {
        slot.setKind(FrameSlotKind.Double);
        return true;
      }
      return false;
    }

    @Override
    protected final boolean isTaggedWith(final Class<?> tag) {
      if (tag == LocalVarWrite.class) {
        return true;
      } else {
        return super.isTaggedWith(tag);
      }
    }

    @Override
    public String toString() {
      return this.getClass().getSimpleName() + "[" + var.name + "]";
    }

    @Override
    public void replaceAfterScopeChange(final InliningVisitor inliner) {
      inliner.updateWrite(var, this, getExp(), 0);
    }
  }
}
