package som.interpreter.nodes.specialized;

import som.interpreter.nodes.TernaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class IfTrueIfFalseMessageNode extends TernaryMessageNode {

  public IfTrueIfFalseMessageNode(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public IfTrueIfFalseMessageNode(final IfTrueIfFalseMessageNode node) { this(node.selector, node.universe); }

  @Specialization(order = 1)
  public SAbstractObject doIfTrueIfFalse(final VirtualFrame frame,
      final boolean receiver, final SBlock trueBlock, final SBlock falseBlock) {
    SMethod branchMethod;
    if (receiver) {
      branchMethod = trueBlock.getMethod();
    } else {
      branchMethod = falseBlock.getMethod();
    }

    SBlock b = universe.newBlock(branchMethod, frame.materialize(), 1);
    return branchMethod.invoke(frame.pack(), b, noArgs);
  }

  @Specialization(guards = "isBooleanReceiver", order = 2)
  public SAbstractObject doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final SBlock trueBlock, final SBlock falseBlock) {
    return doIfTrueIfFalse(frame, receiver == universe.trueObject, trueBlock, falseBlock);
  }

  @Specialization(order = 10)
  public Object doIfTrueIfFalse(final VirtualFrame frame,
      final boolean receiver, final Object trueValue, final SBlock falseBlock) {
    if (receiver) {
      return trueValue;
    } else {
      SMethod branchMethod = falseBlock.getMethod();
      SBlock  b = universe.newBlock(branchMethod, frame.materialize(), 1);
      return branchMethod.invoke(frame.pack(), b, noArgs);
    }
  }

  @Specialization(guards = "isBooleanReceiver", order = 11)
  public Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final Object trueValue, final SBlock falseBlock) {
    return doIfTrueIfFalse(frame, receiver == universe.trueObject, trueValue, falseBlock);
  }

  @Specialization(order = 12)
  public Object doIfTrueIfFalse(final VirtualFrame frame,
      final boolean receiver, final SBlock trueBlock, final Object falseValue) {
    if (receiver) {
      SMethod branchMethod = trueBlock.getMethod();
      SBlock  b = universe.newBlock(branchMethod, frame.materialize(), 1);
      return branchMethod.invoke(frame.pack(), b, noArgs);
    } else {
      return falseValue;
    }
  }

  @Specialization(guards = "isBooleanReceiver", order = 13)
  public Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final SBlock trueBlock, final Object falseValue) {
    return doIfTrueIfFalse(frame, receiver == universe.trueObject, trueBlock, falseValue);
  }

  @Specialization(order = 20)
  public Object doIfTrueIfFalse(final VirtualFrame frame,
      final boolean receiver, final Object trueValue, final Object falseValue) {
    return (receiver) ? trueValue : falseValue;
  }

  @Specialization(guards = "isBooleanReceiver", order = 21)
  public Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final Object trueValue, final Object falseValue) {
    return doIfTrueIfFalse(frame, receiver == universe.trueObject, trueValue, falseValue);
  }
}
