package som.interpreter.nodes;

import static som.interpreter.TruffleCompiler.transferToInterpreter;
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


public abstract class NonLocalVariableNode extends ContextualNode {

  protected final FrameSlot slot;

  private NonLocalVariableNode(final int contextLevel, final FrameSlot slot,
      final FrameSlot localSelf) {
    super(contextLevel, localSelf);
    this.slot = slot;
  }

  public abstract static class NonLocalVariableReadNode extends NonLocalVariableNode {
    public NonLocalVariableReadNode(final int contextLevel,
        final FrameSlot slot, final FrameSlot localSelf) {
      super(contextLevel, slot, localSelf);
    }

    public NonLocalVariableReadNode(final NonLocalVariableReadNode node) {
      this(node.contextLevel, node.slot, node.localSelf);
    }

    @Specialization(guards = "isUninitialized")
    public final SObject doNil() {
      return Universe.current().nilObject;
    }

    @Specialization(guards = "isInitialized", rewriteOn = {FrameSlotTypeException.class})
    public final int doInteger(final VirtualFrame frame) throws FrameSlotTypeException {
      return determineContext(frame).getInt(slot);
    }

    @Specialization(guards = "isInitialized", rewriteOn = {FrameSlotTypeException.class})
    public final double doDouble(final VirtualFrame frame) throws FrameSlotTypeException {
      return determineContext(frame).getDouble(slot);
    }

    @Specialization(guards = "isInitialized", rewriteOn = {FrameSlotTypeException.class})
    public final Object doObject(final VirtualFrame frame) throws FrameSlotTypeException {
      return determineContext(frame).getObject(slot);
    }

    @Generic
    public final Object doGeneric(final VirtualFrame frame) {
      assert isInitialized();
      return FrameUtil.getObjectSafe(determineContext(frame), slot);
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

  public abstract static class NonLocalSuperReadNode
                       extends NonLocalVariableReadNode implements ISuperReadNode {
    private final SClass superClass;

    public NonLocalSuperReadNode(final int contextLevel, final FrameSlot slot,
        final FrameSlot localSelf, final SClass superClass) {
      super(contextLevel, slot, localSelf);
      this.superClass = superClass;
    }

    public NonLocalSuperReadNode(final NonLocalSuperReadNode node) {
      this(node.contextLevel, node.slot, node.localSelf, node.superClass);
    }

    @Override
    public final SClass getSuperClass() {
      return superClass;
    }
  }

  @NodeChild(value = "exp", type = ExpressionNode.class)
  public abstract static class NonLocalVariableWriteNode extends NonLocalVariableNode {

    public NonLocalVariableWriteNode(final int contextLevel,
        final FrameSlot slot, final FrameSlot localSelf) {
      super(contextLevel, slot, localSelf);
    }

    public NonLocalVariableWriteNode(final NonLocalVariableWriteNode node) {
      this(node.contextLevel, node.slot, node.localSelf);
    }

    @Specialization(guards = "isIntKind", rewriteOn = FrameSlotTypeException.class)
    public final int write(final VirtualFrame frame, final int expValue) throws FrameSlotTypeException {
      determineContext(frame).setInt(slot, expValue);
      return expValue;
    }

    @Specialization(guards = "isDoubleKind", rewriteOn = FrameSlotTypeException.class)
    public final double write(final VirtualFrame frame, final double expValue) throws FrameSlotTypeException {
      determineContext(frame).setDouble(slot, expValue);
      return expValue;
    }

    @Generic
    public final Object writeGeneric(final VirtualFrame frame, final Object expValue) {
      ensureObjectKind();
      determineContext(frame).setObject(slot, expValue);
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
  }
}
