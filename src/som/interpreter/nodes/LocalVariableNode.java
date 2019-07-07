package som.interpreter.nodes;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;

import bd.inlining.ScopeAdaptationVisitor;
import bd.tools.nodes.Invocation;
import som.compiler.Variable.Local;
import som.interpreter.nodes.dispatch.TypeCheckNode;
import som.interpreter.nodes.dispatch.TypeCheckNodeGen;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.vm.SomStructuralType;
import som.vm.constants.Nil;
import som.vmobjects.SSymbol;
import tools.debugger.Tags.LocalVariableTag;
import tools.dym.Tags.LocalVarRead;
import tools.dym.Tags.LocalVarWrite;


public abstract class LocalVariableNode extends ExprWithTagsNode
    implements Invocation<SSymbol> {
  protected final FrameSlot       slot;
  protected final FrameDescriptor descriptor;
  protected final Local           var;
  protected final SomStructuralType type;

  @Child protected TypeCheckNode typeCheck;

  // TODO: We currently assume that there is a 1:1 mapping between lexical contexts
  // and frame descriptors, which is apparently not strictly true anymore in Truffle 1.0.0.
  // Generally, we also need to revise everything in this area and address issue #240.
  private LocalVariableNode(final Local var, final SomStructuralType type) {
    this.slot = var.getSlot();
    this.descriptor = var.getFrameDescriptor();
    this.var = var;
    this.type = type;
    this.typeCheck = type == null ? null : TypeCheckNodeGen.create(type, var.source);
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
      return descriptor.getFrameSlotKind(slot) == FrameSlotKind.Illegal;
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
      descriptor.setFrameSlotKind(slot, FrameSlotKind.Object);
      frame.setObject(slot, expValue);
      return expValue;
    }

    // uses expValue to make sure guard is not converted to assertion
    protected final boolean isBoolKind(final boolean expValue) {
      FrameSlotKind kind = descriptor.getFrameSlotKind(slot);
      if (kind == FrameSlotKind.Boolean) {
        return true;
      }
      if (kind == FrameSlotKind.Illegal) {
        descriptor.setFrameSlotKind(slot, FrameSlotKind.Boolean);
        return true;
      }
      return false;
    }

    // uses expValue to make sure guard is not converted to assertion
    protected final boolean isLongKind(final long expValue) {
      FrameSlotKind kind = descriptor.getFrameSlotKind(slot);
      if (kind == FrameSlotKind.Long) {
        return true;
      }
      if (kind == FrameSlotKind.Illegal) {
        descriptor.setFrameSlotKind(slot, FrameSlotKind.Long);
        return true;
      }
      return false;
    }

    // uses expValue to make sure guard is not converted to assertion
    protected final boolean isDoubleKind(final double expValue) {
      FrameSlotKind kind = descriptor.getFrameSlotKind(slot);
      if (kind == FrameSlotKind.Double) {
        return true;
      }
      if (kind == FrameSlotKind.Illegal) {
        descriptor.setFrameSlotKind(slot, FrameSlotKind.Double);
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
