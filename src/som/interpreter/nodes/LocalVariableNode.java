package som.interpreter.nodes;

import static som.interpreter.SNodeFactory.createLocalVariableWrite;
import static som.interpreter.TruffleCompiler.transferToInterpreter;
import som.compiler.Variable;
import som.compiler.Variable.Local;
import som.interpreter.Inliner;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class LocalVariableNode extends ExpressionNode {
  protected final FrameSlot slot;

  private LocalVariableNode(final FrameSlot slot, final SourceSection source) {
    super(source);
    this.slot = slot;
  }

  public final Object getSlotIdentifier() {
    return slot.getIdentifier();
  }

  public abstract static class LocalVariableReadNode extends LocalVariableNode {
    public LocalVariableReadNode(final Variable variable,
        final SourceSection source) {
      super(variable.slot, source);
    }

    public LocalVariableReadNode(final LocalVariableReadNode node) {
      super(node.slot, node.getSourceSection());
    }

    public LocalVariableReadNode(final FrameSlot slot,
        final SourceSection source) {
      super(slot, source);
    }

    @Specialization(guards = "isUninitialized")
    public final SObject doNil() {
      return Universe.current().nilObject;
    }

    @Specialization(guards = "isInitialized", rewriteOn = {FrameSlotTypeException.class})
    public final boolean doBoolean(final VirtualFrame frame) throws FrameSlotTypeException {
      return frame.getBoolean(slot);
    }


    @Specialization(guards = "isInitialized", rewriteOn = {FrameSlotTypeException.class})
    public final long doLong(final VirtualFrame frame) throws FrameSlotTypeException {
      return frame.getLong(slot);
    }

    @Specialization(guards = "isInitialized", rewriteOn = {FrameSlotTypeException.class})
    public final double doDouble(final VirtualFrame frame) throws FrameSlotTypeException {
      return frame.getDouble(slot);
    }

    @Specialization(guards = "isInitialized", rewriteOn = {FrameSlotTypeException.class})
    public final Object doObject(final VirtualFrame frame) throws FrameSlotTypeException {
      return frame.getObject(slot);
    }

//    @Generic
//    public final Object doGeneric(final VirtualFrame frame) {
//      assert isInitialized();
//      return FrameUtil.getObjectSafe(frame, slot);
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

  public abstract static class LocalSuperReadNode
                       extends LocalVariableReadNode implements ISuperReadNode {
    private final SClass superClass;

    public LocalSuperReadNode(final Variable variable, final SClass superClass,
        final SourceSection source) {
      this(variable.slot, superClass, source);
    }

    public LocalSuperReadNode(final FrameSlot slot, final SClass superClass,
        final SourceSection source) {
      super(slot, source);
      this.superClass = superClass;
    }

    public LocalSuperReadNode(final LocalSuperReadNode node) {
      this(node.slot, node.superClass, node.getSourceSection());
    }

    @Override
    public final SClass getSuperClass() {
      return superClass;
    }
  }

  @NodeChild(value = "exp", type = ExpressionNode.class)
  public abstract static class LocalVariableWriteNode extends LocalVariableNode {

    public LocalVariableWriteNode(final Local variable, final SourceSection source) {
      super(variable.slot, source);
    }

    public LocalVariableWriteNode(final LocalVariableWriteNode node) {
      super(node.slot, node.getSourceSection());
    }

    public LocalVariableWriteNode(final FrameSlot slot, final SourceSection source) {
      super(slot, source);
    }

    public abstract ExpressionNode getExp();

    @Specialization(guards = "isBoolKind", rewriteOn = FrameSlotTypeException.class)
    public final boolean write(final VirtualFrame frame, final boolean expValue) throws FrameSlotTypeException {
      frame.setBoolean(slot, expValue);
      return expValue;
    }

    @Specialization(guards = "isLongKind", rewriteOn = FrameSlotTypeException.class)
    public final long write(final VirtualFrame frame, final long expValue) throws FrameSlotTypeException {
      frame.setLong(slot, expValue);
      return expValue;
    }

    @Specialization(guards = "isDoubleKind", rewriteOn = FrameSlotTypeException.class)
    public final double write(final VirtualFrame frame, final double expValue) throws FrameSlotTypeException {
      frame.setDouble(slot, expValue);
      return expValue;
    }

    @Specialization
    public final Object writeGeneric(final VirtualFrame frame, final Object expValue) {
      ensureObjectKind();
      frame.setObject(slot, expValue);
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

    @Override
    public final void replaceWithIndependentCopyForInlining(final Inliner inliner) {
      CompilerAsserts.neverPartOfCompilation("replaceWithIndependentCopyForInlining");

      if (getParent() instanceof ArgumentInitializationNode) {
        FrameSlot varSlot = inliner.getLocalFrameSlot(getSlotIdentifier());
        assert varSlot != null;
        replace(createLocalVariableWrite(varSlot, getExp(), getSourceSection()));
      } else {
        throw new RuntimeException("Should not be part of an uninitalized tree. And this should only be done with uninitialized trees.");
      }
    }
  }
}
