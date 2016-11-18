package som.primitives.arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.interpreter.SArguments;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.MessageSendNode.GenericMessageSendNode;
import som.interpreter.nodes.nary.BinaryBasicOperation;
import som.primitives.Primitive;
import som.vm.Symbols;
import som.vm.constants.KernelObj;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import tools.dym.Tags.ArrayRead;


@GenerateNodeFactory
@Primitive(primitive = "array:at:", selector = "at:", receiverType = SArray.class)
public abstract class AtPrim extends BinaryBasicOperation {
  private final ValueProfile storageType = ValueProfile.createClassProfile();

  protected AtPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == ArrayRead.class) {
      return true;
    } else {
      return super.isTaggedWithIgnoringEagerness(tag);
    }
  }

  @Specialization(guards = "receiver.isEmptyType()", rewriteOn = IndexOutOfBoundsException.class)
  public final Object doEmptySArray(final SArray receiver, final long idx) {
    if (idx < 1 || idx > receiver.getEmptyStorage(storageType)) {
      CompilerDirectives.transferToInterpreter();
      VM.thisMethodNeedsToBeOptimized("This is currently not fast, but programs relying on it are broken.");
      throw new IndexOutOfBoundsException();
    }
    return Nil.nilObject;
  }

  protected GenericMessageSendNode createIdxOutOfBoundsExSend() {
    return MessageSendNode.createGeneric(Symbols.symbolFor("signalWith:index:"), null, getSourceSection());
  }

  @Specialization(guards = "receiver.isEmptyType()")
  public final Object doEmptySArrayAndThrowException(final VirtualFrame frame,
      final SArray receiver, final long idx,
      @Cached("createIdxOutOfBoundsExSend()") final GenericMessageSendNode ex) {
    if (idx < 1 || idx > receiver.getEmptyStorage(storageType)) {
      int rcvrIdx = SArguments.RCVR_IDX; assert rcvrIdx == 0;
      return ex.doPreEvaluated(frame, new Object[] {KernelObj.indexOutOfBoundsClass, receiver, idx});
    }
    return Nil.nilObject;
  }

  @Specialization(guards = "receiver.isPartiallyEmptyType()", rewriteOn = IndexOutOfBoundsException.class)
  public final Object doPartiallyEmptySArray(final SArray receiver, final long idx) {
    return receiver.getPartiallyEmptyStorage(storageType).get(idx - 1);
  }

  @Specialization(guards = "receiver.isPartiallyEmptyType()")
  public final Object doPartiallyEmptySArray(final VirtualFrame frame,
      final SArray receiver, final long idx,
      @Cached("createIdxOutOfBoundsExSend()") final GenericMessageSendNode ex) {
    try {
      return receiver.getPartiallyEmptyStorage(storageType).get(idx - 1);
    } catch (IndexOutOfBoundsException e) {
      int rcvrIdx = SArguments.RCVR_IDX; assert rcvrIdx == 0;
      return ex.doPreEvaluated(frame, new Object[] {KernelObj.indexOutOfBoundsClass, receiver, idx});
    }
  }

  @Specialization(guards = "receiver.isObjectType()", rewriteOn = IndexOutOfBoundsException.class)
  public final Object doObjectSArray(final SArray receiver, final long idx) {
    return receiver.getObjectStorage(storageType)[(int) idx - 1];
  }

  @Specialization(guards = "receiver.isObjectType()")
  public final Object doObjectSArray(final VirtualFrame frame,
      final SArray receiver, final long idx,
      @Cached("createIdxOutOfBoundsExSend()") final GenericMessageSendNode ex) {
    try {
      return receiver.getObjectStorage(storageType)[(int) idx - 1];
    } catch (IndexOutOfBoundsException e) {
      int rcvrIdx = SArguments.RCVR_IDX; assert rcvrIdx == 0;
      return ex.doPreEvaluated(frame, new Object[] {KernelObj.indexOutOfBoundsClass, receiver, idx});
    }
  }

  @Specialization(guards = "receiver.isLongType()", rewriteOn = IndexOutOfBoundsException.class)
  public final long doLongSArray(final SArray receiver, final long idx) {
    return receiver.getLongStorage(storageType)[(int) idx - 1];
  }

  @Specialization(guards = "receiver.isLongType()")
  public final long doLongSArray(final VirtualFrame frame,
      final SArray receiver, final long idx,
      @Cached("createIdxOutOfBoundsExSend()") final GenericMessageSendNode ex) {
    try {
      return receiver.getLongStorage(storageType)[(int) idx - 1];
    } catch (IndexOutOfBoundsException e) {
      int rcvrIdx = SArguments.RCVR_IDX; assert rcvrIdx == 0;
      return (long) ex.doPreEvaluated(frame, new Object[] {KernelObj.indexOutOfBoundsClass, receiver, idx});
    }
  }

  @Specialization(guards = "receiver.isDoubleType()", rewriteOn = IndexOutOfBoundsException.class)
  public final double doDoubleSArray(final SArray receiver, final long idx) {
    return receiver.getDoubleStorage(storageType)[(int) idx - 1];
  }

  @Specialization(guards = "receiver.isDoubleType()")
  public final double doDoubleSArray(final VirtualFrame frame,
      final SArray receiver, final long idx,
      @Cached("createIdxOutOfBoundsExSend()") final GenericMessageSendNode ex) {
    try {
      return receiver.getDoubleStorage(storageType)[(int) idx - 1];
    } catch (IndexOutOfBoundsException e) {
      int rcvrIdx = SArguments.RCVR_IDX; assert rcvrIdx == 0;
      return (double) ex.doPreEvaluated(frame, new Object[] {KernelObj.indexOutOfBoundsClass, receiver, idx});
    }
  }

  @Specialization(guards = "receiver.isBooleanType()", rewriteOn = IndexOutOfBoundsException.class)
  public final boolean doBooleanSArray(final SArray receiver, final long idx) {
    return receiver.getBooleanStorage(storageType)[(int) idx - 1];
  }

  @Specialization(guards = "receiver.isBooleanType()")
  public final boolean doBooleanSArray(final VirtualFrame frame,
      final SArray receiver, final long idx,
      @Cached("createIdxOutOfBoundsExSend()") final GenericMessageSendNode ex) {
    try {
      return receiver.getBooleanStorage(storageType)[(int) idx - 1];
    } catch (IndexOutOfBoundsException e) {
      int rcvrIdx = SArguments.RCVR_IDX; assert rcvrIdx == 0;
      return (boolean) ex.doPreEvaluated(frame, new Object[] {KernelObj.indexOutOfBoundsClass, receiver, idx});
    }
  }
}
