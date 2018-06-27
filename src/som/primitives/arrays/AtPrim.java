package som.primitives.arrays;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;

import bd.primitives.Primitive;
import bd.primitives.Specializer;
import som.VM;
import som.interpreter.Invokable;
import som.interpreter.SArguments;
import som.interpreter.nodes.ExceptionSignalingNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.BinaryBasicOperation;
import som.interpreter.transactions.TxArrayAccessFactory.TxBinaryArrayOpNodeGen;
import som.primitives.arrays.AtPrim.TxAtPrim;
import som.vm.Symbols;
import som.vm.constants.KernelObj;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SSymbol;
import tools.dym.Tags.ArrayRead;


@GenerateNodeFactory
@Primitive(primitive = "array:at:", selector = "at:", receiverType = SArray.class,
    inParser = false, specializer = TxAtPrim.class)
public abstract class AtPrim extends BinaryBasicOperation {
  protected static final class TxAtPrim extends Specializer<VM, ExpressionNode, SSymbol> {
    public TxAtPrim(final Primitive prim, final NodeFactory<ExpressionNode> fact,
        final VM vm) {
      super(prim, fact, vm);
    }

    @Override
    public ExpressionNode create(final Object[] arguments,
        final ExpressionNode[] argNodes, final SourceSection section,
        final boolean eagerWrapper) {
      ExpressionNode node = super.create(arguments, argNodes, section, eagerWrapper);

      // TODO: seems a bit expensive,
      // might want to optimize for interpreter first iteration speed
      // TODO: clone in UnitializedDispatchNode.AbstractUninitialized.forAtomic()
      RootNode root = argNodes[0].getRootNode();
      boolean forAtomic;
      if (root instanceof Invokable) {
        forAtomic = ((Invokable) root).isAtomic();
      } else {
        // TODO: need to think about integration with actors, but, that's a
        // later research project
        forAtomic = false;
      }

      if (forAtomic) {
        return TxBinaryArrayOpNodeGen.create((BinaryBasicOperation) node, null, null)
                                     .initialize(section, eagerWrapper);
      } else {
        return node;
      }
    }
  }

  private final ValueProfile storageType = ValueProfile.createClassProfile();

  @Child protected ExceptionSignalingNode indexOutOfBounds;

  public abstract Object execute(VirtualFrame frame, SArray array, long index);

  @Override
  @SuppressWarnings("unchecked")
  public AtPrim initialize(final SourceSection sourceSection) {
    super.initialize(sourceSection);
    indexOutOfBounds = insert(ExceptionSignalingNode.createNode(KernelObj.kernel,
        Symbols.IndexOutOfBounds, Symbols.SIGNAL_WITH_IDX, sourceSection));
    return this;
  }

  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == ArrayRead.class) {
      return true;
    } else {
      return super.isTaggedWithIgnoringEagerness(tag);
    }
  }

  private Object triggerException(final SArray arr, final long idx) {
    int rcvrIdx = SArguments.RCVR_IDX;
    assert rcvrIdx == 0;
    return indexOutOfBounds.signal(arr, idx);
  }

  @Specialization(guards = "receiver.isEmptyType()")
  public final Object doEmptySArray(final SArray receiver, final long idx) {
    if (idx < 1 || idx > receiver.getEmptyStorage(storageType)) {
      return triggerException(receiver, idx);
    }
    return Nil.nilObject;
  }

  @Specialization(guards = "receiver.isPartiallyEmptyType()")
  public final Object doPartiallyEmptySArray(final SArray receiver, final long idx) {
    try {
      return receiver.getPartiallyEmptyStorage(storageType).get(idx - 1);
    } catch (IndexOutOfBoundsException e) {
      return triggerException(receiver, idx);
    }
  }

  @Specialization(guards = "receiver.isObjectType()")
  public final Object doObjectSArray(final SArray receiver, final long idx) {
    try {
      return receiver.getObjectStorage(storageType)[(int) idx - 1];
    } catch (IndexOutOfBoundsException e) {
      return triggerException(receiver, idx);
    }
  }

  @Specialization(guards = "receiver.isLongType()")
  public final long doLongSArray(final SArray receiver, final long idx) {
    try {
      return receiver.getLongStorage(storageType)[(int) idx - 1];
    } catch (IndexOutOfBoundsException e) {
      return (long) triggerException(receiver, idx);
    }
  }

  @Specialization(guards = "receiver.isDoubleType()")
  public final double doDoubleSArray(final SArray receiver, final long idx) {
    try {
      return receiver.getDoubleStorage(storageType)[(int) idx - 1];
    } catch (IndexOutOfBoundsException e) {
      return (double) triggerException(receiver, idx);
    }
  }

  @Specialization(guards = "receiver.isBooleanType()")
  public final boolean doBooleanSArray(final SArray receiver, final long idx) {
    try {
      return receiver.getBooleanStorage(storageType)[(int) idx - 1];
    } catch (IndexOutOfBoundsException e) {
      return (boolean) triggerException(receiver, idx);
    }
  }
}
