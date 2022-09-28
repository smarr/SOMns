package som.interpreter.nodes;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;

import bd.inlining.ScopeAdaptationVisitor;
import bd.tools.nodes.Invocation;
import som.compiler.Variable.Local;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.vm.constants.Nil;
import som.vmobjects.SSymbol;
import tools.debugger.Tags.LocalVariableTag;
import tools.dym.Tags.LocalVarRead;
import tools.dym.Tags.LocalVarWrite;


public abstract class LocalVariableNode extends ExprWithTagsNode
    implements Invocation<SSymbol> {
  protected final int slotIndex;

  protected final Local var;

  // TODO: We currently assume that there is a 1:1 mapping between lexical contexts
  // and frame descriptors, which is apparently not strictly true anymore in Truffle 1.0.0.
  // Generally, we also need to revise everything in this area and address issue #240.
  private LocalVariableNode(final Local var) {
    this.slotIndex = var.getSlotIndex();
    this.var = var;
  }

  public final Local getLocal() {
    return var;
  }

  @Override
  public final SSymbol getInvocationIdentifier() {
    return var.name;
  }

  @Override
  public boolean hasTag(final Class<? extends Tag> tag) {
    if (tag == LocalVariableTag.class) {
      return true;
    } else {
      return super.hasTag(tag);
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

    @Specialization(guards = {"frame.isBoolean(slotIndex)"},
        rewriteOn = {FrameSlotTypeException.class})
    public final boolean doBoolean(final VirtualFrame frame) throws FrameSlotTypeException {
      return frame.getBoolean(slotIndex);
    }

    @Specialization(guards = {"frame.isLong(slotIndex)"},
        rewriteOn = {FrameSlotTypeException.class})
    public final long doLong(final VirtualFrame frame) throws FrameSlotTypeException {
      return frame.getLong(slotIndex);
    }

    @Specialization(guards = {"frame.isDouble(slotIndex)"},
        rewriteOn = {FrameSlotTypeException.class})
    public final double doDouble(final VirtualFrame frame) throws FrameSlotTypeException {
      return frame.getDouble(slotIndex);
    }

    @Specialization(guards = {"frame.isObject(slotIndex)"},
        replaces = {"doBoolean", "doLong", "doDouble"},
        rewriteOn = {FrameSlotTypeException.class})
    public final Object doObject(final VirtualFrame frame) throws FrameSlotTypeException {
      return frame.getObject(slotIndex);
    }

    protected final boolean isUninitialized(final VirtualFrame frame) {
      return var.getFrameDescriptor().getSlotKind(slotIndex) == FrameSlotKind.Illegal;
    }

    @Override
    public boolean hasTag(final Class<? extends Tag> tag) {
      if (tag == LocalVarRead.class) {
        return true;
      } else {
        return super.hasTag(tag);
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

    public LocalVariableWriteNode(final Local variable) {
      super(variable);
    }

    public LocalVariableWriteNode(final LocalVariableWriteNode node) {
      super(node.var);
    }

    public abstract ExpressionNode getExp();

    @Specialization(guards = "isBoolKind(expValue)")
    public final boolean writeBoolean(final VirtualFrame frame, final boolean expValue) {
      frame.setBoolean(slotIndex, expValue);
      return expValue;
    }

    @Specialization(guards = "isLongKind(expValue)")
    public final long writeLong(final VirtualFrame frame, final long expValue) {
      frame.setLong(slotIndex, expValue);
      return expValue;
    }

    @Specialization(guards = "isDoubleKind(expValue)")
    public final double writeDouble(final VirtualFrame frame, final double expValue) {
      frame.setDouble(slotIndex, expValue);
      return expValue;
    }

    @Specialization(replaces = {"writeBoolean", "writeLong", "writeDouble"})
    public final Object writeGeneric(final VirtualFrame frame, final Object expValue) {
      var.getFrameDescriptor().setSlotKind(slotIndex, FrameSlotKind.Object);
      frame.setObject(slotIndex, expValue);
      return expValue;
    }

    // uses expValue to make sure guard is not converted to assertion
    protected final boolean isBoolKind(final boolean expValue) {
      FrameDescriptor desc = var.getFrameDescriptor();
      FrameSlotKind kind = desc.getSlotKind(slotIndex);
      if (kind == FrameSlotKind.Boolean) {
        return true;
      }
      if (kind == FrameSlotKind.Illegal) {
        desc.setSlotKind(slotIndex, FrameSlotKind.Boolean);
        return true;
      }
      return false;
    }

    // uses expValue to make sure guard is not converted to assertion
    protected final boolean isLongKind(final long expValue) {
      FrameDescriptor desc = var.getFrameDescriptor();
      FrameSlotKind kind = desc.getSlotKind(slotIndex);
      if (kind == FrameSlotKind.Long) {
        return true;
      }
      if (kind == FrameSlotKind.Illegal) {
        desc.setSlotKind(slotIndex, FrameSlotKind.Long);
        return true;
      }
      return false;
    }

    // uses expValue to make sure guard is not converted to assertion
    protected final boolean isDoubleKind(final double expValue) {
      FrameDescriptor desc = var.getFrameDescriptor();
      FrameSlotKind kind = desc.getSlotKind(slotIndex);
      if (kind == FrameSlotKind.Double) {
        return true;
      }
      if (kind == FrameSlotKind.Illegal) {
        desc.setSlotKind(slotIndex, FrameSlotKind.Double);
        return true;
      }
      return false;
    }

    @Override
    public boolean hasTag(final Class<? extends Tag> tag) {
      if (tag == LocalVarWrite.class) {
        return true;
      } else {
        return super.hasTag(tag);
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
