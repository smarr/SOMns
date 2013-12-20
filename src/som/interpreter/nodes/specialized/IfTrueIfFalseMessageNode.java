package som.interpreter.nodes.specialized;

import som.interpreter.Arguments;
import som.interpreter.nodes.messages.TernarySendNode;
import som.vmobjects.SBlock;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class IfTrueIfFalseMessageNode extends TernarySendNode {

  public IfTrueIfFalseMessageNode(final TernarySendNode node) { super(node); }
  public IfTrueIfFalseMessageNode(final IfTrueIfFalseMessageNode node) { super(node); }

  @Specialization(order = 1)
  public Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final SBlock trueBlock, final SBlock falseBlock) {
    SMethod branchMethod;
    Arguments context;
    if (receiver == universe.trueObject) {
      branchMethod = trueBlock.getMethod();
      context      = trueBlock.getContext(); // TODO: test whether the current implementation is correct, or whether it should be the following: Method.getUpvalues(frame);
    } else {
      assert receiver == universe.falseObject;
      branchMethod = falseBlock.getMethod();
      context      = falseBlock.getContext(); // TODO: test whether the current implementation is correct, or whether it should be the following: Method.getUpvalues(frame);
    }

    SBlock b = universe.newBlock(branchMethod, context);
    return branchMethod.invoke(frame.pack(), b, universe);
  }

  @Specialization(order = 2)
  public Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final Object trueValue, final SBlock falseBlock) {
    if (receiver == universe.trueObject) {
      return trueValue;
    } else {
      assert receiver == universe.falseObject;
      SMethod   branchMethod = falseBlock.getMethod();
      Arguments context      = falseBlock.getContext(); // TODO: test whether the current implementation is correct, or whether it should be the following: Method.getUpvalues(frame);
      SBlock b = universe.newBlock(branchMethod, context);
      return branchMethod.invoke(frame.pack(), b, universe);
    }
  }

  @Specialization(order = 3)
  public Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final SBlock trueBlock, final Object falseValue) {
    if (receiver == universe.trueObject) {
      SMethod branchMethod = trueBlock.getMethod();
      Arguments context    = trueBlock.getContext(); // TODO: test whether the current implementation is correct, or whether it should be the following: Method.getUpvalues(frame);
      SBlock  b = universe.newBlock(branchMethod, context);
      return branchMethod.invoke(frame.pack(), b, universe);
    } else {
      assert receiver == universe.falseObject;
      return falseValue;
    }
  }

  @Specialization(order = 4)
  public Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final Object trueValue, final Object falseValue) {
    if (receiver == universe.trueObject) {
      return trueValue;
    } else {
      assert receiver == universe.falseObject;
      return falseValue;
    }
  }
}
