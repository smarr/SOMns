package som.interpreter.nodes;

import static som.interpreter.TruffleCompiler.transferToInterpreter;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;


public abstract class NonLocalVariableNode extends ContextualNode {

  protected final FrameSlot slot;

  private NonLocalVariableNode(final int contextLevel, final FrameSlot slot,
      final FrameSlot localSelf, final SourceSection source) {
    super(contextLevel, localSelf, source);
    this.slot = slot;
  }

  public abstract static class NonLocalVariableReadNode extends NonLocalVariableNode {

    public NonLocalVariableReadNode(final int contextLevel,
        final FrameSlot slot, final FrameSlot localSelf, final SourceSection source) {
      super(contextLevel, slot, localSelf, source);
    }

    public NonLocalVariableReadNode(final NonLocalVariableReadNode node) {
      this(node.contextLevel, node.slot, node.localSelf, node.getSourceSection());
    }

    @Specialization(guards = "isUninitialized")
    public final SObject doNil() {
      return Nil.nilObject;
    }

    @Specialization(guards = "isInitialized", rewriteOn = {FrameSlotTypeException.class})
    public final boolean doBoolean(final VirtualFrame frame) throws FrameSlotTypeException {
      return determineContext(frame).getBoolean(slot);
    }

    @Specialization(guards = "isInitialized", rewriteOn = {FrameSlotTypeException.class})
    public final long doLong(final VirtualFrame frame) throws FrameSlotTypeException {
      return determineContext(frame).getLong(slot);
    }

    @Specialization(guards = "isInitialized", rewriteOn = {FrameSlotTypeException.class})
    public final double doDouble(final VirtualFrame frame) throws FrameSlotTypeException {
      return determineContext(frame).getDouble(slot);
    }

    @Specialization(guards = "isInitialized", rewriteOn = {FrameSlotTypeException.class})
    public final Object doObject(final VirtualFrame frame) throws FrameSlotTypeException {
      return CompilerDirectives.unsafeCast(
          determineContext(frame).getObject(slot), Object.class, true, true);
    }

//    @Generic
//    public final Object doGeneric(final VirtualFrame frame) {
//      assert isInitialized();
//      return FrameUtil.getObjectSafe(determineContext(frame), slot);
//    }

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
        final FrameSlot localSelf, final SClass superClass, final SourceSection source) {
      super(contextLevel, slot, localSelf, source);
      this.superClass = superClass;
    }

    public NonLocalSuperReadNode(final NonLocalSuperReadNode node) {
      this(node.contextLevel, node.slot, node.localSelf, node.superClass, node.getSourceSection());
    }

    @Override
    public final SClass getSuperClass() {
      return superClass;
    }
  }

  @NodeChild(value = "exp", type = ExpressionNode.class)
  public abstract static class NonLocalVariableWriteNode extends NonLocalVariableNode {

    public NonLocalVariableWriteNode(final int contextLevel,
        final FrameSlot slot, final FrameSlot localSelf, final SourceSection source) {
      super(contextLevel, slot, localSelf, source);
    }

    public NonLocalVariableWriteNode(final NonLocalVariableWriteNode node) {
      this(node.contextLevel, node.slot, node.localSelf, node.getSourceSection());
    }

    @Specialization(guards = "isBoolKind")
    public final boolean writeBoolean(final VirtualFrame frame, final boolean expValue) {
      determineContext(frame).setBoolean(slot, expValue);
      return expValue;
    }

    @Specialization(guards = "isLongKind")
    public final long writeLong(final VirtualFrame frame, final long expValue) {
      determineContext(frame).setLong(slot, expValue);
      return expValue;
    }

    @Specialization(guards = "isDoubleKind")
    public final double writeDouble(final VirtualFrame frame, final double expValue) {
      determineContext(frame).setDouble(slot, expValue);
      return expValue;
    }

    @Specialization(contains = {"writeBoolean", "writeLong", "writeDouble"})
    public final Object writeGeneric(final VirtualFrame frame, final Object expValue) {
      ensureObjectKind();
      determineContext(frame).setObject(slot, expValue);
      return expValue;
    }

    protected final boolean isBoolKind() {
      if (slot.getKind() == FrameSlotKind.Boolean) {
        return true;
      }
      if (slot.getKind() == FrameSlotKind.Illegal) {
        transferToInterpreter("LocalVar.writeBoolToUninit");
        slot.setKind(FrameSlotKind.Boolean);
        return true;
      }
      return false;
    }

    protected final boolean isLongKind() {
      if (slot.getKind() == FrameSlotKind.Long) {
        return true;
      }
      if (slot.getKind() == FrameSlotKind.Illegal) {
        transferToInterpreter("LocalVar.writeIntToUninit");
        slot.setKind(FrameSlotKind.Long);
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
