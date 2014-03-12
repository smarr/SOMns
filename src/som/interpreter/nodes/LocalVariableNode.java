package som.interpreter.nodes;

import static som.interpreter.TruffleCompiler.transferToInterpreter;
import som.compiler.Variable;
import som.compiler.Variable.Local;
import som.interpreter.Inliner;
import som.interpreter.nodes.LocalVariableNodeFactory.LocalVariableWriteNodeFactory;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Generic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class LocalVariableNode extends ExpressionNode {
  protected final FrameSlot slot;

  private LocalVariableNode(final FrameSlot slot) {
    this.slot = slot;
  }

  public Object getSlotIdentifier() {
    return slot.getIdentifier();
  }

  public abstract static class LocalVariableReadNode extends LocalVariableNode {
    public LocalVariableReadNode(final Variable variable) {
      super(variable.slot);
    }

    public LocalVariableReadNode(final LocalVariableReadNode node) {
      super(node.slot);
    }

    public LocalVariableReadNode(final FrameSlot slot) {
      super(slot);
    }

    @Specialization(guards = "isUninitialized")
    public SObject doNil() {
      return Universe.current().nilObject;
    }

    @Specialization(guards = "isInitialized", rewriteOn = {FrameSlotTypeException.class})
    public int doInteger(final VirtualFrame frame) throws FrameSlotTypeException {
      return frame.getInt(slot);
    }

    @Specialization(guards = "isInitialized", rewriteOn = {FrameSlotTypeException.class})
    public double doDouble(final VirtualFrame frame) throws FrameSlotTypeException {
      return frame.getDouble(slot);
    }

    @Specialization(guards = "isInitialized", rewriteOn = {FrameSlotTypeException.class})
    public Object doObject(final VirtualFrame frame) throws FrameSlotTypeException {
      return frame.getObject(slot);
    }

    @Generic
    public Object doGeneric(final VirtualFrame frame) {
      assert isInitialized();
      return FrameUtil.getObjectSafe(frame, slot);
    }

    protected final boolean isInitialized() {
      return slot.getKind() != FrameSlotKind.Illegal;
    }

    protected final boolean isUninitialized() {
      return slot.getKind() == FrameSlotKind.Illegal;
    }

    @Override
    public final void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }
  }

  public abstract static class LocalSuperReadNode
                       extends LocalVariableReadNode implements ISuperReadNode {
    private final SClass superClass;

    public LocalSuperReadNode(final Variable variable, final SClass superClass) {
      this(variable.slot, superClass);
    }

    public LocalSuperReadNode(final FrameSlot slot, final SClass superClass) {
      super(slot);
      this.superClass = superClass;
    }

    public LocalSuperReadNode(final LocalSuperReadNode node) {
      this(node.slot, node.superClass);
    }

    @Override
    public final SClass getSuperClass() {
      return superClass;
    }
  }

  @NodeChild(value = "exp", type = ExpressionNode.class)
  public abstract static class LocalVariableWriteNode extends LocalVariableNode {

    public LocalVariableWriteNode(final Local variable) {
      super(variable.slot);
    }

    public LocalVariableWriteNode(final LocalVariableWriteNode node) {
      super(node.slot);
    }

    public LocalVariableWriteNode(final FrameSlot slot) {
      super(slot);
    }

    public abstract ExpressionNode getExp();

    @Specialization(guards = "isIntKind", rewriteOn = FrameSlotTypeException.class)
    public int write(final VirtualFrame frame, final int expValue) throws FrameSlotTypeException {
      frame.setInt(slot, expValue);
      return expValue;
    }

    @Specialization(guards = "isDoubleKind", rewriteOn = FrameSlotTypeException.class)
    public double write(final VirtualFrame frame, final double expValue) throws FrameSlotTypeException {
      frame.setDouble(slot, expValue);
      return expValue;
    }

    @Generic
    public Object writeGeneric(final VirtualFrame frame, final Object expValue) {
      ensureObjectKind();
      frame.setObject(slot, expValue);
      return expValue;
    }

    protected final boolean isIntKind() {
      if (slot.getKind() == FrameSlotKind.Int) {
        return true;
      }
      if (slot.getKind() == FrameSlotKind.Illegal) {
        transferToInterpreter("LocalVar.writeIntToUninit");
        slot.setKind(FrameSlotKind.Int);
        return true;
      }
      return false;
    }

    protected final boolean isDoubleKind() {
      if (slot.getKind() == FrameSlotKind.Double) {
        return true;
      }
      if (slot.getKind() == FrameSlotKind.Illegal) {
        transferToInterpreter("LocalVar.writeDoubleToUninit");
        slot.setKind(FrameSlotKind.Double);
        return true;
      }
      return false;
    }

    protected final void ensureObjectKind() {
      if (slot.getKind() != FrameSlotKind.Object) {
        transferToInterpreter("LocalVar.writeObjectToUninit");
        slot.setKind(FrameSlotKind.Object);
      }
    }

    @Override
    public void replaceWithIndependentCopyForInlining(final Inliner inliner) {
      if (getParent() instanceof ArgumentInitializationNode) {
        FrameSlot varSlot = inliner.getLocalFrameSlot(getSlotIdentifier());
        assert varSlot != null;
        replace(LocalVariableWriteNodeFactory.create(varSlot, getExp()));
      } else {
        throw new RuntimeException("Should not be part of an uninitalized tree. And this should only be done with uninitialized trees.");
      }
    }
  }
}
