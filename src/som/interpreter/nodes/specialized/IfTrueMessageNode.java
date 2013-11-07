package som.interpreter.nodes.specialized;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.messages.BinaryMonomorphicNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class IfTrueMessageNode extends BinaryMonomorphicNode {
  public IfTrueMessageNode(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
  public IfTrueMessageNode(final IfTrueMessageNode node) { this(node.selector, node.universe, node.rcvrClass, node.invokable); }

  /**
   * This is the case were we got a block as the argument. Need to actually
   * evaluate it.
   */
  @Specialization(order = 1)
  public SAbstractObject doIfTrue(final VirtualFrame frame,
      final boolean receiver, final SBlock argument) {
    if (receiver) {
      SMethod blockMethod = argument.getMethod();
      SBlock b = universe.newBlock(blockMethod, frame.materialize(), 1);
      return blockMethod.invoke(frame.pack(), b, noArgs);
    } else {
      return universe.nilObject;
    }
  }

  @Specialization(guards = "isBooleanReceiver", order = 2)
  public SAbstractObject doIfTrue(final VirtualFrame frame,
      final SObject receiver, final SBlock argument) {
    return doIfTrue(frame, receiver == universe.trueObject, argument);
  }

  /**
   * The argument in this case is an expression and can be returned directly.
   */
  @Specialization(order = 10)
  public SAbstractObject doIfTrue(final VirtualFrame frame,
      final boolean receiver, final SAbstractObject argument) {
    if (receiver) {
      return argument;
    } else {
      return universe.nilObject;
    }
  }

  @Specialization(guards = "isBooleanReceiver", order = 11)
  public SAbstractObject doIfTrue(final VirtualFrame frame,
      final SObject receiver, final SAbstractObject argument) {
    return doIfTrue(frame, receiver == universe.trueObject, argument);
  }

  @Override
  public ExpressionNode cloneForInlining() {
    throw new NotImplementedException();
//    return IfTrueIfFalseMessageNodeFactory.create(selector, universe,
//        blockMethodTrueBranch, blockMethodFalseBranch, getReceiver(),
//        getArguments());
  }
}
