package som.interpreter.nodes;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;

import bd.inlining.ScopeAdaptationVisitor;
import som.compiler.Variable.Local;
import som.interpreter.nodes.dispatch.TypeCheckNode;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.vm.SomStructuralType;
import som.vm.constants.Nil;
import som.vmobjects.SSymbol;
import tools.Send;
import tools.debugger.Tags.LocalVariableTag;
import tools.dym.Tags.LocalVarRead;
import tools.dym.Tags.LocalVarWrite;


public abstract class LocalVariableNode extends ExprWithTagsNode implements Send {
  protected final FrameSlot slot;
  protected final Local     var;

  @Child protected TypeCheckNode typeCheck;

  private LocalVariableNode(final Local var, final SomStructuralType type) {
    this.slot = var.getSlot();
    this.var = var;
    this.typeCheck = type == null ? null : TypeCheckNode.create(type, var.source);
  }

  public final Local getLocal() {
    return var;
  }

  @Override
  public final SSymbol getSelector() {
    return var.name;
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

    public LocalVariableReadNode(final Local variable, final SomStructuralType type) {
      super(variable, type);
    }

    public LocalVariableReadNode(final LocalVariableReadNode node,
        final SomStructuralType type) {
      this(node.var, type);
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
      boolean ret = frame.getBoolean(slot);
      if (typeCheck != null) {
        typeCheck.executeTypeCheck(ret);
      }
      return ret;
    }

    @Specialization(guards = {"isLong(frame)"}, rewriteOn = {FrameSlotTypeException.class})
    public final long doLong(final VirtualFrame frame) throws FrameSlotTypeException {
      long ret = frame.getLong(slot);
      if (typeCheck != null) {
        typeCheck.executeTypeCheck(ret);
      }
      return ret;
    }

    @Specialization(guards = {"isDouble(frame)"}, rewriteOn = {FrameSlotTypeException.class})
    public final double doDouble(final VirtualFrame frame) throws FrameSlotTypeException {
      double ret = frame.getDouble(slot);
      if (typeCheck != null) {
        typeCheck.executeTypeCheck(ret);
      }
      return ret;
    }

    @Specialization(guards = {"isObject(frame)"},
        replaces = {"doBoolean", "doLong", "doDouble"},
        rewriteOn = {FrameSlotTypeException.class})
    public final Object doObject(final VirtualFrame frame)
        throws FrameSlotTypeException, IllegalArgumentException {
      Object ret = frame.getObject(slot);
      if (typeCheck != null) {
        typeCheck.executeTypeCheck(ret);
      }
      return ret;
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
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateRead(var, this, 0);
    }
  }

  @NodeChild(value = "exp", type = ExpressionNode.class)
  public abstract static class LocalVariableWriteNode extends LocalVariableNode {

    public LocalVariableWriteNode(final Local variable, final SomStructuralType type) {
      super(variable, type);
    }

    public LocalVariableWriteNode(final LocalVariableWriteNode node,
        final SomStructuralType type) {
      super(node.var, type);
    }

    public abstract ExpressionNode getExp();

    @Specialization(guards = "isBoolKind(expValue)")
    public final boolean writeBoolean(final VirtualFrame frame, final boolean expValue) {
      if (typeCheck != null) {
        typeCheck.executeTypeCheck(expValue);
      }
      frame.setBoolean(slot, expValue);
      return expValue;
    }

    @Specialization(guards = "isLongKind(expValue)")
    public final long writeLong(final VirtualFrame frame, final long expValue) {
      if (typeCheck != null) {
        typeCheck.executeTypeCheck(expValue);
      }
      frame.setLong(slot, expValue);
      return expValue;
    }

    @Specialization(guards = "isDoubleKind(expValue)")
    public final double writeDouble(final VirtualFrame frame, final double expValue) {
      if (typeCheck != null) {
        typeCheck.executeTypeCheck(expValue);
      }
      frame.setDouble(slot, expValue);
      return expValue;
    }

    @Specialization(replaces = {"writeBoolean", "writeLong", "writeDouble"})
    public final Object writeGeneric(final VirtualFrame frame, final Object expValue) {
      if (typeCheck != null) {
        typeCheck.executeTypeCheck(expValue);
      }
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
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateWrite(var, this, getExp(), 0);
    }
  }
}
