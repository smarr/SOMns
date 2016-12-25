package som.interpreter.nodes;

import static som.interpreter.TruffleCompiler.transferToInterpreter;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.Variable.Local;
import som.interpreter.InlinerAdaptToEmbeddedOuterContext;
import som.interpreter.InlinerForLexicallyEmbeddedMethods;
import som.interpreter.SplitterForLexicallyEmbeddedCode;
import som.vm.constants.Nil;
import tools.debugger.Tags.LocalVariableTag;
import tools.dym.Tags.LocalVarRead;
import tools.dym.Tags.LocalVarWrite;


public abstract class NonLocalVariableNode extends ContextualNode {

  protected final FrameSlot slot;
  protected final Local var;

  private NonLocalVariableNode(final int contextLevel, final Local var,
      final SourceSection source) {
    super(contextLevel, source);
    this.slot = var.getSlot();
    this.var  = var;
  }

  @Override
  public abstract void replaceWithLexicallyEmbeddedNode(
      final InlinerForLexicallyEmbeddedMethods inliner);

  @Override
  public abstract void replaceWithCopyAdaptedToEmbeddedOuterContext(
      final InlinerAdaptToEmbeddedOuterContext inliner);

  @Override
  protected boolean isTaggedWith(final Class<?> tag) {
    if (tag == LocalVariableTag.class) {
      return true;
    } else {
      return super.isTaggedWith(tag);
    }
  }

  public abstract static class NonLocalVariableReadNode extends NonLocalVariableNode {

    public NonLocalVariableReadNode(final int contextLevel,
        final Local var, final SourceSection source) {
      super(contextLevel, var, source);
    }

    public NonLocalVariableReadNode(final NonLocalVariableReadNode node) {
      this(node.contextLevel, node.var, node.sourceSection);
    }

    @Specialization(guards = "isUninitialized(frame)")
    public final Object doNil(final VirtualFrame frame) {
      return Nil.nilObject;
    }

    protected boolean isBoolean(final VirtualFrame frame) {
      return determineContext(frame).isBoolean(slot);
    }

    protected boolean isLong(final VirtualFrame frame) {
      return determineContext(frame).isLong(slot);
    }

    protected boolean isDouble(final VirtualFrame frame) {
      return determineContext(frame).isDouble(slot);
    }

    protected boolean isObject(final VirtualFrame frame) {
      return determineContext(frame).isObject(slot);
    }

    @Specialization(guards = {"isBoolean(frame)"}, rewriteOn = {FrameSlotTypeException.class})
    public final boolean doBoolean(final VirtualFrame frame) throws FrameSlotTypeException {
      return determineContext(frame).getBoolean(slot);
    }

    @Specialization(guards = {"isLong(frame)"}, rewriteOn = {FrameSlotTypeException.class})
    public final long doLong(final VirtualFrame frame) throws FrameSlotTypeException {
      return determineContext(frame).getLong(slot);
    }

    @Specialization(guards = {"isDouble(frame)"}, rewriteOn = {FrameSlotTypeException.class})
    public final double doDouble(final VirtualFrame frame) throws FrameSlotTypeException {
      return determineContext(frame).getDouble(slot);
    }

    @Specialization(guards = {"isObject(frame)"},
        contains = {"doBoolean", "doLong", "doDouble"},
        rewriteOn = {FrameSlotTypeException.class})
    public final Object doObject(final VirtualFrame frame) throws FrameSlotTypeException {
      return determineContext(frame).getObject(slot);
    }

    protected final boolean isUninitialized(final VirtualFrame frame) {
      return slot.getKind() == FrameSlotKind.Illegal;
    }

    @Override
    protected boolean isTaggedWith(final Class<?> tag) {
      if (tag == LocalVarRead.class) {
        return true;
      } else {
        return super.isTaggedWith(tag);
      }
    }

    @Override
    public void replaceWithIndependentCopyForInlining(
        final SplitterForLexicallyEmbeddedCode inliner) {
      Local var = (Local) inliner.getSplitVar(this.var);
      replace(var.getReadNode(contextLevel, sourceSection));
    }

    @Override
    public void replaceWithLexicallyEmbeddedNode(
        final InlinerForLexicallyEmbeddedMethods inliner) {
      Local var = (Local) inliner.getSplitVar(this.var);
      if (contextLevel == 0) {
        replace(var.getReadNode(contextLevel, sourceSection));
      } else {
        replace(var.getReadNode(contextLevel - 1, sourceSection));
      }
    }

    @Override
    public void replaceWithCopyAdaptedToEmbeddedOuterContext(
        final InlinerAdaptToEmbeddedOuterContext inliner) {
      Local var = (Local) inliner.getSplitVar(this.var);

      // if the context level is 1, the variable is in the outer context,
      // which just got inlined, so, we need to adapt the slot id
      if (inliner.appliesTo(contextLevel)) {
        replace(var.getReadNode(contextLevel, sourceSection));
      } else if (inliner.needToAdjustLevel(contextLevel)) {
        replace(var.getReadNode(contextLevel - 1, sourceSection));
      }
    }
  }

  @NodeChild(value = "exp", type = ExpressionNode.class)
  public abstract static class NonLocalVariableWriteNode extends NonLocalVariableNode {

    public NonLocalVariableWriteNode(final int contextLevel,
        final Local var, final SourceSection source) {
      super(contextLevel, var, source);
    }

    public NonLocalVariableWriteNode(final NonLocalVariableWriteNode node) {
      this(node.contextLevel, node.var, node.sourceSection);
    }

    public abstract ExpressionNode getExp();

    @Specialization(guards = "isBoolKind(frame)")
    public final boolean writeBoolean(final VirtualFrame frame, final boolean expValue) {
      determineContext(frame).setBoolean(slot, expValue);
      return expValue;
    }

    @Specialization(guards = "isLongKind(frame)")
    public final long writeLong(final VirtualFrame frame, final long expValue) {
      determineContext(frame).setLong(slot, expValue);
      return expValue;
    }

    @Specialization(guards = "isDoubleKind(frame)")
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

    protected final boolean isBoolKind(final VirtualFrame frame) {
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

    protected final boolean isLongKind(final VirtualFrame frame) {
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

    protected final boolean isDoubleKind(final VirtualFrame frame) {
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
    protected final boolean isTaggedWith(final Class<?> tag) {
      if (tag == LocalVarWrite.class) {
        return true;
      } else {
        return super.isTaggedWith(tag);
      }
    }

    @Override
    public void replaceWithIndependentCopyForInlining(
        final SplitterForLexicallyEmbeddedCode inliner) {
      Local var = (Local) inliner.getSplitVar(this.var);
      replace(var.getWriteNode(contextLevel, this.getExp(), sourceSection));
    }

    @Override
    public void replaceWithLexicallyEmbeddedNode(
        final InlinerForLexicallyEmbeddedMethods inliner) {
      Local var = (Local) inliner.getSplitVar(this.var);
      if (contextLevel == 0) {
        replace(var.getWriteNode(contextLevel, this.getExp(), sourceSection));
      } else {
        replace(var.getWriteNode(contextLevel - 1, this.getExp(), sourceSection));
      }
    }

    @Override
    public void replaceWithCopyAdaptedToEmbeddedOuterContext(
        final InlinerAdaptToEmbeddedOuterContext inliner) {
      Local var = (Local) inliner.getSplitVar(this.var);

      // if the context level is 1, the variable is in the outer context,
      // which just got inlined, so, we need to adapt the slot id
      if (inliner.appliesTo(contextLevel)) {
        replace(var.getWriteNode(contextLevel, this.getExp(), sourceSection));
      } else if (inliner.needToAdjustLevel(contextLevel)) {
        replace(var.getWriteNode(contextLevel - 1, this.getExp(), sourceSection));
      }
    }
  }
}
