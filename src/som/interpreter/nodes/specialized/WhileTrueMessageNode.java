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


public abstract class WhileTrueMessageNode extends BinaryMonomorphicNode {
  public WhileTrueMessageNode(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
  public WhileTrueMessageNode(final WhileTrueMessageNode node) { this(node.selector, node.universe, node.rcvrClass, node.invokable); }

  private Object executeBlock(final VirtualFrame frame,
      final SBlock block) {
    SMethod method = block.getMethod();
    SBlock b = universe.newBlock(method, frame.materialize(), 1);
    return method.invoke(frame.pack(), b, noArgs);
  }

  protected boolean receiverIsTrueObject(final SObject receiver) {
    return receiver == universe.trueObject;
  }

  @Specialization
  public SAbstractObject doWhileTrue(final VirtualFrame frame,
      final SBlock loopCondition, final SBlock loopBody) {
    SAbstractObject loopConditionResult = (SAbstractObject) executeBlock(frame, loopCondition);

    // TODO: this is a simplification, we don't cover the case receiver isn't a boolean
    while (loopConditionResult == universe.trueObject) {
      executeBlock(frame, loopBody);
      loopConditionResult = (SAbstractObject) executeBlock(frame, loopCondition);
    }

    return universe.nilObject;
  }

  @Specialization(guards = "receiverIsTrueObject")
  public SAbstractObject doWhileTrue(final VirtualFrame frame,
      final SObject loopCondition, final SBlock loopBody) {
    while (true) {
      executeBlock(frame, loopBody);
    }
  }

  @Override
  public ExpressionNode cloneForInlining() {
    throw new NotImplementedException();
//    return WhileMessageNodeFactory.create(selector, universe,
//        blockMethodCondition, blockMethodLoopBody, whileTrue, getReceiver(),
//        getArguments());
  }
}
