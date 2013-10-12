package som.interpreter.nodes.specialized;

import som.interpreter.nodes.AbstractMessageNode;
import som.interpreter.nodes.ExpressionNode;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class IfTrueIfFalseMessageNode extends AbstractMessageNode {

  private final SMethod blockMethodTrueBranch;
  private final SMethod blockMethodFalseBranch;

  private static final SObject[] noArgs = new SObject[0];

  public IfTrueIfFalseMessageNode(final SSymbol selector,
      final Universe universe, final SBlock trueBlock,
      final SBlock falseBlock) {
    this(selector, universe,
        (trueBlock  != null) ? trueBlock.getMethod()  : null,
        (falseBlock != null) ? falseBlock.getMethod() : null);
  }

  public IfTrueIfFalseMessageNode(final SSymbol selector,
      final Universe universe, final SMethod trueBlockMethod,
      final SMethod falseBlockMethod) {
    super(selector, universe);
    blockMethodTrueBranch  = trueBlockMethod;
    blockMethodFalseBranch = falseBlockMethod;
  }

  public IfTrueIfFalseMessageNode(final IfTrueIfFalseMessageNode node) {
    this(node.selector, node.universe, node.blockMethodTrueBranch,
        node.blockMethodFalseBranch);
  }

  @Specialization
  public SObject doIfTrueIfFalse(final VirtualFrame frame,
      final SObject receiver, final Object arguments) {
    SObject trueExpResult  = null;
    SObject falseExpResult = null;

    SObject[] args = (SObject[]) arguments;

    if (blockMethodTrueBranch == null) {
      trueExpResult = args[0];
    }
    if (blockMethodFalseBranch == null) {
      falseExpResult = args[1];
    }

    return evaluateBody(frame, receiver, arguments, trueExpResult, falseExpResult);
  }

  public SObject evaluateBody(final VirtualFrame frame, final SObject rcvr,
      final Object arguments,
      final SObject trueResult, final SObject falseResult) {
    SClass currentRcvrClass = classOfReceiver(rcvr, getReceiver());

    if (currentRcvrClass == universe.trueClass) {
      if (blockMethodTrueBranch == null) {
        return trueResult;
      } else {
        SBlock b = universe.newBlock(blockMethodTrueBranch, frame.materialize(), 1);
        return blockMethodTrueBranch.invoke(frame.pack(), b, noArgs);
      }
    } else if (currentRcvrClass == universe.falseClass) {
      if (blockMethodFalseBranch == null) {
        return falseResult;
      } else {
        SBlock b = universe.newBlock(blockMethodFalseBranch, frame.materialize(), 1);
        return blockMethodFalseBranch.invoke(frame.pack(), b, noArgs);
      }
    } else {
      return fallbackForNonBoolReceiver(currentRcvrClass).
          doGeneric(frame, rcvr, arguments);
    }
  }

  private PolymorpicMessageNode fallbackForNonBoolReceiver(final SClass currentRcvrClass) {
    CompilerDirectives.transferToInterpreter();

    // So, it might just be a polymorphic send site.
    PolymorpicMessageNode poly = PolymorpicMessageNodeFactory.create(selector,
        universe, currentRcvrClass, getReceiver(),
        getArguments());
    return replace(poly, "Receiver wasn't a boolean. " +
        "So, we need to do the actual send.");
  }

  @Override
  public ExpressionNode cloneForInlining() {
    return IfTrueIfFalseMessageNodeFactory.create(selector, universe,
        blockMethodTrueBranch, blockMethodFalseBranch, getReceiver(),
        getArguments());
  }
}
