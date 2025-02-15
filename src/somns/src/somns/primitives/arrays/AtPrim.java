package somns.primitives.arrays;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import bd.primitives.Primitive;
import bd.primitives.Specializer;
import somns.interpreter.transactions.TxArrayAccessFactory.TxBinaryArrayOpNodeGen;
import somns.VM;
import somns.interpreter.Invokable;
import somns.interpreter.SArguments;
import somns.interpreter.nodes.ExceptionSignalingNode;
import somns.interpreter.nodes.ExpressionNode;
import somns.interpreter.nodes.nary.BinaryBasicOperation;
import somns.primitives.arrays.AtPrim.TxAtPrim;
import somns.vm.Symbols;
import somns.vm.constants.KernelObj;
import somns.vm.constants.Nil;
import somns.vmobjects.SArray;
import somns.vmobjects.SSymbol;
import tools.dym.Tags.ArrayRead;


@GenerateNodeFactory
@Primitive(primitive = "array:at:", selector = "at:", receiverType = SArray.class,
    inParser = false, specializer = TxAtPrim.class)
public abstract class AtPrim extends BinaryBasicOperation {
  protected static final class TxAtPrim extends Specializer<VM, ExpressionNode, SSymbol> {
    public TxAtPrim(final Primitive prim, final NodeFactory<ExpressionNode> fact) {
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
        return TxBinaryArrayOpNodeGen.create((BinaryBasicOperation) node, null, null)
                                     .initialize(section, eagerWrapper);
      } else {
        return node;
      }
    }
  }

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
  protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
    if (tag == ArrayRead.class) {
      return true;
    } else {
      return super.hasTagIgnoringEagerness(tag);
    }
  }

  private Object triggerException(final SArray arr, final long idx) {
    int rcvrIdx = SArguments.RCVR_IDX;
    assert rcvrIdx == 0;
    return indexOutOfBounds.signal(arr, idx);
  }

  @Specialization(guards = "receiver.isEmptyType()")
  public final Object doEmptySArray(final SArray receiver, final long idx) {
    if (idx < 1 || idx > receiver.getEmptyStorage()) {
      return triggerException(receiver, idx);
    }
    return Nil.nilObject;
  }

  @Specialization(guards = "receiver.isPartiallyEmptyType()")
  public final Object doPartiallyEmptySArray(final SArray receiver, final long idx) {
    try {
      return receiver.getPartiallyEmptyStorage().get(idx - 1);
    } catch (IndexOutOfBoundsException e) {
      return triggerException(receiver, idx);
    }
  }

  @Specialization(guards = "receiver.isObjectType()")
  public final Object doObjectSArray(final SArray receiver, final long idx) {
    try {
      return receiver.getObjectStorage()[(int) idx - 1];
    } catch (IndexOutOfBoundsException e) {
      return triggerException(receiver, idx);
    }
  }

  @Specialization(guards = "receiver.isLongType()")
  public final long doLongSArray(final SArray receiver, final long idx) {
    try {
      return receiver.getLongStorage()[(int) idx - 1];
    } catch (IndexOutOfBoundsException e) {
      return (long) triggerException(receiver, idx);
    }
  }

  @Specialization(guards = "receiver.isDoubleType()")
  public final double doDoubleSArray(final SArray receiver, final long idx) {
    try {
      return receiver.getDoubleStorage()[(int) idx - 1];
    } catch (IndexOutOfBoundsException e) {
      return (double) triggerException(receiver, idx);
    }
  }

  @Specialization(guards = "receiver.isBooleanType()")
  public final boolean doBooleanSArray(final SArray receiver, final long idx) {
    try {
      return receiver.getBooleanStorage()[(int) idx - 1];
    } catch (IndexOutOfBoundsException e) {
      return (boolean) triggerException(receiver, idx);
    }
  }
}
