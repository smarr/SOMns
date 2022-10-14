package som.primitives.arrays;

import java.util.Arrays;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import bd.primitives.Primitive;
import bd.primitives.Specializer;
import som.VM;
import som.interpreter.Invokable;
import som.interpreter.SArguments;
import som.interpreter.nodes.ExceptionSignalingNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.transactions.TxArrayAccessFactory.TxTernaryArrayOpNodeGen;
import som.primitives.arrays.AtPutPrim.TxAtPutPrim;
import som.vm.Symbols;
import som.vm.constants.KernelObj;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SArray.PartiallyEmptyArray;
import som.vmobjects.SArray.SMutableArray;
import som.vmobjects.SSymbol;
import tools.dym.Tags.ArrayWrite;
import tools.dym.Tags.BasicPrimitiveOperation;


@GenerateNodeFactory
@ImportStatic(Nil.class)
@Primitive(primitive = "array:at:put:", selector = "at:put:",
    receiverType = SArray.class, inParser = false, specializer = TxAtPutPrim.class)
public abstract class AtPutPrim extends TernaryExpressionNode {
  protected static final class TxAtPutPrim extends Specializer<VM, ExpressionNode, SSymbol> {
    public TxAtPutPrim(final Primitive prim, final NodeFactory<ExpressionNode> fact) {
      super(prim, fact);
    }

    @Override
    public ExpressionNode create(final Object[] arguments,
        final ExpressionNode[] argNodes, final SourceSection section,
        final boolean eagerWrapper, final VM vm) {
      ExpressionNode node = super.create(arguments, argNodes, section, eagerWrapper, vm);
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
        return TxTernaryArrayOpNodeGen.create((TernaryExpressionNode) node, null, null, null)
                                      .initialize(section, eagerWrapper);
      } else {
        return node;
      }
    }
  }

  @Child protected ExceptionSignalingNode indexOutOfBounds;

  @Override
  @SuppressWarnings("unchecked")
  public AtPutPrim initialize(final SourceSection sourceSection) {
    super.initialize(sourceSection);
    indexOutOfBounds = insert(ExceptionSignalingNode.createNode(KernelObj.kernel,
        Symbols.IndexOutOfBounds, Symbols.SIGNAL_WITH_IDX, sourceSection));
    return this;
  }

  @Override
  protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
    if (tag == BasicPrimitiveOperation.class) {
      return true;
    } else if (tag == ArrayWrite.class) {
      return true;
    } else {
      return super.hasTagIgnoringEagerness(tag);
    }
  }

  protected static final boolean valueIsNotLong(final Object value) {
    return !(value instanceof Long);
  }

  protected static final boolean valueIsNotDouble(final Object value) {
    return !(value instanceof Double);
  }

  protected static final boolean valueIsNotBoolean(final Object value) {
    return !(value instanceof Boolean);
  }

  protected static final boolean valueNotLongDoubleBoolean(final Object value) {
    return !(value instanceof Long) &&
        !(value instanceof Double) &&
        !(value instanceof Boolean);
  }

  private Object triggerException(final VirtualFrame frame, final SArray arr, final long idx) {
    int rcvrIdx = SArguments.RCVR_IDX;
    assert rcvrIdx == 0;
    return indexOutOfBounds.signal(frame, arr, idx);
  }

  private static void setValue(final long idx, final Object value,
      final PartiallyEmptyArray storage) {
    if (storage.get(idx) == Nil.nilObject) {
      storage.decEmptyElements();
    }
    storage.set(idx, value);
  }

  private Object transitionAndSet(final SMutableArray receiver,
      final long index, final Object value, final Object[] newStorage) {
    receiver.transitionTo(newStorage);
    newStorage[(int) index - 1] = value;
    return value;
  }

  private void setAndPossiblyTransition(final SMutableArray receiver,
      final long index, final Object value, final PartiallyEmptyArray.Type expectedType) {
    PartiallyEmptyArray storage = receiver.getPartiallyEmptyStorage();
    setValue(index - 1, value, storage);
    if (storage.getType() != expectedType) {
      storage.setType(PartiallyEmptyArray.Type.OBJECT);
    }
    receiver.ifFullOrObjectTransitionPartiallyEmpty();
  }

  @Specialization(guards = {"receiver.isEmptyType()"})
  public final long doEmptySArray(final VirtualFrame frame, final SMutableArray receiver,
      final long index, final long value) {
    try {
      receiver.transitionFromEmptyToPartiallyEmptyWith(index - 1, value);
      return value;
    } catch (IndexOutOfBoundsException e) {
      return (long) triggerException(frame, receiver, index);
    }
  }

  @Specialization(guards = {"receiver.isEmptyType()"})
  public final double doEmptySArray(final VirtualFrame frame, final SMutableArray receiver,
      final long index, final double value) {
    try {
      receiver.transitionFromEmptyToPartiallyEmptyWith(index - 1, value);
      return value;
    } catch (IndexOutOfBoundsException e) {
      return (double) triggerException(frame, receiver, index);
    }
  }

  @Specialization(guards = {"receiver.isEmptyType()"})
  public final boolean doEmptySArray(final VirtualFrame frame, final SMutableArray receiver,
      final long index, final boolean value) {
    try {
      receiver.transitionFromEmptyToPartiallyEmptyWith(index - 1, value);
      return value;
    } catch (IndexOutOfBoundsException e) {
      return (boolean) triggerException(frame, receiver, index);
    }
  }

  @Specialization(guards = {"receiver.isEmptyType()", "valueIsNotNil(value)",
      "valueNotLongDoubleBoolean(value)"})
  public final Object doEmptySArray(final VirtualFrame frame, final SMutableArray receiver,
      final long index, final Object value) {
    final int idx = (int) index - 1;
    int size = receiver.getEmptyStorage();

    // if the value is an object, we transition directly to an Object array
    Object[] newStorage = new Object[size];
    Arrays.fill(newStorage, Nil.nilObject);
    try {
      newStorage[idx] = value;
      receiver.transitionTo(newStorage);
      return value;
    } catch (IndexOutOfBoundsException e) {
      return triggerException(frame, receiver, index);
    }
  }

  @Specialization(guards = {"receiver.isEmptyType()", "valueIsNil(value)"})
  public final Object doEmptySArrayWithNil(final VirtualFrame frame,
      final SMutableArray receiver, final long index, final Object value) {
    long idx = index - 1;
    if (idx < 0 || idx >= receiver.getEmptyStorage()) {
      return triggerException(frame, receiver, index);
    }
    return Nil.nilObject;
  }

  @Specialization(guards = "receiver.isPartiallyEmptyType()")
  public final long doPartiallyEmptySArray(final VirtualFrame frame,
      final SMutableArray receiver, final long index, final long value) {
    try {
      setAndPossiblyTransition(receiver, index, value, PartiallyEmptyArray.Type.LONG);
      return value;
    } catch (IndexOutOfBoundsException e) {
      return (long) triggerException(frame, receiver, index);
    }
  }

  @Specialization(guards = "receiver.isPartiallyEmptyType()")
  public final double doPartiallyEmptySArray(final VirtualFrame frame,
      final SMutableArray receiver, final long index, final double value) {
    try {
      setAndPossiblyTransition(receiver, index, value, PartiallyEmptyArray.Type.DOUBLE);
      return value;
    } catch (IndexOutOfBoundsException e) {
      return (double) triggerException(frame, receiver, index);
    }
  }

  @Specialization(guards = "receiver.isPartiallyEmptyType()")
  public final boolean doPartiallyEmptySArray(final VirtualFrame frame,
      final SMutableArray receiver, final long index, final boolean value) {
    try {
      setAndPossiblyTransition(receiver, index, value, PartiallyEmptyArray.Type.BOOLEAN);
      return value;
    } catch (IndexOutOfBoundsException e) {
      return (boolean) triggerException(frame, receiver, index);
    }
  }

  @Specialization(guards = {"receiver.isPartiallyEmptyType()", "valueIsNil(value)"})
  public final Object doPartiallyEmptySArrayWithNil(final VirtualFrame frame,
      final SMutableArray receiver, final long index, final Object value) {
    long idx = index - 1;
    PartiallyEmptyArray storage = receiver.getPartiallyEmptyStorage();

    try {
      if (storage.get(idx) != Nil.nilObject) {
        storage.incEmptyElements();
        storage.set(idx, Nil.nilObject);
      }
      return value;
    } catch (IndexOutOfBoundsException e) {
      return triggerException(frame, receiver, index);
    }
  }

  @Specialization(guards = {"receiver.isPartiallyEmptyType()", "valueIsNotNil(value)"})
  public final Object doPartiallyEmptySArray(final VirtualFrame frame,
      final SMutableArray receiver, final long index, final Object value) {
    try {
      setAndPossiblyTransition(receiver, index, value, PartiallyEmptyArray.Type.OBJECT);
      return value;
    } catch (IndexOutOfBoundsException e) {
      return triggerException(frame, receiver, index);
    }
  }

  @Specialization(guards = "receiver.isObjectType()")
  public final Object doObjectSArray(final VirtualFrame frame, final SMutableArray receiver,
      final long index, final Object value) {
    try {
      receiver.getObjectStorage()[(int) index - 1] = value;
      return value;
    } catch (IndexOutOfBoundsException e) {
      return triggerException(frame, receiver, index);
    }
  }

  @Specialization(guards = "receiver.isLongType()")
  public final long doObjectSArray(final VirtualFrame frame, final SMutableArray receiver,
      final long index, final long value) {
    try {
      receiver.getLongStorage()[(int) index - 1] = value;
      return value;
    } catch (IndexOutOfBoundsException e) {
      return (long) triggerException(frame, receiver, index);
    }
  }

  @Specialization(guards = {"receiver.isLongType()", "valueIsNotLong(value)"})
  public final Object doLongSArray(final VirtualFrame frame, final SMutableArray receiver,
      final long index, final Object value) {
    long[] storage = receiver.getLongStorage();
    Object[] newStorage = new Object[storage.length];
    for (int i = 0; i < storage.length; i++) {
      newStorage[i] = storage[i];
    }

    try {
      return transitionAndSet(receiver, index, value, newStorage);
    } catch (IndexOutOfBoundsException e) {
      return triggerException(frame, receiver, index);
    }
  }

  @Specialization(guards = "receiver.isDoubleType()")
  public final double doDoubleSArray(final VirtualFrame frame, final SMutableArray receiver,
      final long index, final double value) {
    try {
      receiver.getDoubleStorage()[(int) index - 1] = value;
      return value;
    } catch (IndexOutOfBoundsException e) {
      return (double) triggerException(frame, receiver, index);
    }
  }

  @Specialization(guards = {"receiver.isDoubleType()", "valueIsNotDouble(value)"})
  public final Object doDoubleSArray(final VirtualFrame frame, final SMutableArray receiver,
      final long index, final Object value) {
    double[] storage = receiver.getDoubleStorage();
    Object[] newStorage = new Object[storage.length];
    for (int i = 0; i < storage.length; i++) {
      newStorage[i] = storage[i];
    }
    try {
      return transitionAndSet(receiver, index, value, newStorage);
    } catch (IndexOutOfBoundsException e) {
      return triggerException(frame, receiver, index);
    }
  }

  @Specialization(guards = "receiver.isBooleanType()")
  public final boolean doBooleanSArray(final VirtualFrame frame, final SMutableArray receiver,
      final long index, final boolean value) {
    try {
      receiver.getBooleanStorage()[(int) index - 1] = value;
      return value;
    } catch (IndexOutOfBoundsException e) {
      return (boolean) triggerException(frame, receiver, index);
    }
  }

  @Specialization(guards = {"receiver.isBooleanType()", "valueIsNotBoolean(value)"})
  public final Object doBooleanSArray(final VirtualFrame frame, final SMutableArray receiver,
      final long index, final Object value) {
    boolean[] storage = receiver.getBooleanStorage();
    Object[] newStorage = new Object[storage.length];
    for (int i = 0; i < storage.length; i++) {
      newStorage[i] = storage[i];
    }
    try {
      return transitionAndSet(receiver, index, value, newStorage);
    } catch (IndexOutOfBoundsException e) {
      return triggerException(frame, receiver, index);
    }
  }
}
