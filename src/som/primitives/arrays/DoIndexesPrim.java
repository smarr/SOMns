package som.primitives.arrays;

import som.interpreter.Invokable;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.UninitializedValuePrimDispatchNode;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.primitives.BlockPrims.ValuePrimitiveNode;
import som.vmobjects.SArray;
import som.vmobjects.SArray.ArrayType;
import som.vmobjects.SBlock;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public abstract class DoIndexesPrim extends BinaryExpressionNode
    implements ValuePrimitiveNode {
  @Child private AbstractDispatchNode block;

  public DoIndexesPrim() {
    super(null);
    block = new UninitializedValuePrimDispatchNode();
  }

  public final static boolean isEmptyType(final SArray receiver) {
    return receiver.getType() == ArrayType.EMPTY;
  }

  public final static boolean isPartiallyEmptyType(final SArray receiver) {
    return receiver.getType() == ArrayType.PARTIAL_EMPTY;
  }

  public final static boolean isObjectType(final SArray receiver) {
    return receiver.getType() == ArrayType.OBJECT;
  }

  public final static boolean isLongType(final SArray receiver) {
    return receiver.getType() == ArrayType.LONG;
  }

  public final static boolean isDoubleType(final SArray receiver) {
    return receiver.getType() == ArrayType.DOUBLE;
  }

  @Specialization(guards = "isEmptyType")
  public final SArray doEmptyArray(final VirtualFrame frame,
      final SArray receiver, final SBlock block) {
    int length = receiver.getEmptyStorage();
    loop(frame, block, length);
    return receiver;
  }

  @Specialization(guards = "isPartiallyEmptyType")
  public final SArray doPartiallyEmptyArray(final VirtualFrame frame,
      final SArray receiver, final SBlock block) {
    int length = receiver.getPartiallyEmptyStorage().getLength();
    loop(frame, block, length);
    return receiver;
  }

  @Specialization(guards = "isObjectType")
  public final SArray doObjectArray(final VirtualFrame frame,
      final SArray receiver, final SBlock block) {
    int length = receiver.getObjectStorage().length;
    loop(frame, block, length);
    return receiver;
  }

  @Specialization(guards = "isLongType")
  public final SArray doLongArray(final VirtualFrame frame,
      final SArray receiver, final SBlock block) {
    int length = receiver.getLongStorage().length;
    loop(frame, block, length);
    return receiver;
  }

  @Specialization(guards = "isDoubleType")
  public final SArray doDoubleArray(final VirtualFrame frame,
      final SArray receiver, final SBlock block) {
    int length = receiver.getDoubleStorage().length;
    loop(frame, block, length);
    return receiver;
  }

  private void loop(final VirtualFrame frame, final SBlock block, final int length) {
    try {
      assert SArray.FIRST_IDX == 0;
      if (SArray.FIRST_IDX < length) {
        this.block.executeDispatch(frame, new Object[] {
            block, (long) SArray.FIRST_IDX + 1}); // +1 because it is going to the smalltalk level
      }
      for (long i = 1; i < length; i++) {
        this.block.executeDispatch(frame, new Object[] {
            block, i + 1}); // +1 because it is going to the smalltalk level
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        reportLoopCount(length);
      }
    }
  }

  protected final void reportLoopCount(final long count) {
    assert count >= 0;
    CompilerAsserts.neverPartOfCompilation("reportLoopCount");
    Node current = getParent();
    while (current != null && !(current instanceof RootNode)) {
      current = current.getParent();
    }
    if (current != null) {
      ((Invokable) current).propagateLoopCountThroughoutLexicalScope(count);
    }
  }

  @Override
  public void adoptNewDispatchListHead(final AbstractDispatchNode node) {
    block = insert(node);
  }
}
