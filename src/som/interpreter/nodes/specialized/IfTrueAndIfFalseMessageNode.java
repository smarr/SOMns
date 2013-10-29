package som.interpreter.nodes.specialized;

import som.interpreter.nodes.AbstractMessageNode;
import som.interpreter.nodes.ExpressionNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Generic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class IfTrueAndIfFalseMessageNode extends AbstractMessageNode {
  private final SMethod blockMethod;
  private final boolean executeIf;
  private static final SAbstractObject[] noArgs = new SAbstractObject[0];

  public IfTrueAndIfFalseMessageNode(final SSymbol selector,
      final Universe universe, final SBlock block, final boolean executeIf) {
    this(selector, universe, block.getMethod(), executeIf);
  }

  public IfTrueAndIfFalseMessageNode(final SSymbol selector,
      final Universe universe, final SMethod blockMethod, final boolean executeIf) {
    super(selector, universe);
    this.blockMethod = blockMethod;
    this.executeIf = executeIf;
  }

  public IfTrueAndIfFalseMessageNode(final IfTrueAndIfFalseMessageNode node) {
    this(node.selector, node.universe, node.blockMethod, node.executeIf);
  }

  @Specialization(order = 10, guards = {"isIfTrue", "isBooleanReceiver"})
  public SAbstractObject doIfTrue(final VirtualFrame frame, final SAbstractObject receiver,
      final Object arguments) {
    SClass rcvrClass = classOfReceiver(receiver, getReceiver());
    if (rcvrClass == universe.trueClass) {
      SBlock b = universe.newBlock(blockMethod, frame.materialize(), 1);
      return blockMethod.invoke(frame.pack(), b, noArgs);
    } else {
      return universe.nilObject;
    }
  }

  @Specialization(order = 20, guards = {"isIfFalse", "isBooleanReceiver"})
  public SAbstractObject doIfFalse(final VirtualFrame frame, final SAbstractObject receiver,
      final Object arguments) {
    SClass rcvrClass = classOfReceiver(receiver, getReceiver());
    if (rcvrClass == universe.falseClass) {
      SBlock b = universe.newBlock(blockMethod, frame.materialize(), 1);
      return blockMethod.invoke(frame.pack(), b, noArgs);
    } else {
      return universe.nilObject;
    }
  }

  public boolean isIfTrue() {
    return executeIf;
  }

  public boolean isIfFalse() {
    return !executeIf;
  }

  @Generic
  public SAbstractObject doGeneric(final VirtualFrame frame, final SAbstractObject receiver,
      final Object arguments) {
    if (!isBooleanReceiver(receiver)) {
      return fallbackForNonBoolReceiver(receiver).
          doGeneric(frame, receiver, arguments);
    }
    if (executeIf) {
      return doIfTrue(frame, receiver, arguments);
    } else {
      return doIfFalse(frame, receiver, arguments);
    }
  }

  protected PolymorpicMessageNode fallbackForNonBoolReceiver(final SAbstractObject receiver) {
    CompilerDirectives.transferToInterpreter();

    SClass rcvrClass = classOfReceiver(receiver, getReceiver());

    // So, it might just be a polymorphic send site.
    PolymorpicMessageNode poly = PolymorpicMessageNodeFactory.create(selector,
        universe, rcvrClass, getReceiver(), getArguments());
    return replace(poly, "Receiver wasn't a boolean. " +
        "So, we need to do the actual send.");
  }

  /**
   * @return uninitialized node to allow for specialization
   */
  @Override
  public ExpressionNode cloneForInlining() {
    return IfTrueAndIfFalseMessageNodeFactory.create(selector, universe,
        blockMethod, executeIf, getReceiver(), getArguments());
  }

}
