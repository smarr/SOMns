package som.interpreter.nodes.specialized;

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
    if (receiver == universe.trueObject) {
      branchMethod = trueBlock.getMethod();
    } else {
      assert receiver == universe.falseObject;
      branchMethod = falseBlock.getMethod();
    }

    SBlock b = universe.newBlock(branchMethod, frame.materialize(), 1);
    return branchMethod.invoke(frame.pack(), b);
  }

  @Specialization(order = 2)
  public Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final Object trueValue, final SBlock falseBlock) {
    if (receiver == universe.trueObject) {
      return trueValue;
    } else {
      assert receiver == universe.falseObject;
      SMethod branchMethod = falseBlock.getMethod();
      SBlock  b = universe.newBlock(branchMethod, frame.materialize(), 1);
      return branchMethod.invoke(frame.pack(), b);
    }
  }

  @Specialization(order = 3)
  public Object doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final SBlock trueBlock, final Object falseValue) {
    if (receiver == universe.trueObject) {
      SMethod branchMethod = trueBlock.getMethod();
      SBlock  b = universe.newBlock(branchMethod, frame.materialize(), 1);
      return branchMethod.invoke(frame.pack(), b);
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
