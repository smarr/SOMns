package som.interpreter.nodes;

import som.interpreter.TruffleCompiler;
import som.interpreter.TypesGen;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.GenericDispatchNode;
import som.interpreter.nodes.dispatch.SuperDispatchNode;
import som.interpreter.nodes.dispatch.UninitializedDispatchNode;
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.nary.EagerBinaryPrimitiveNode;
import som.interpreter.nodes.nary.EagerUnaryPrimitiveNode;
import som.interpreter.nodes.specialized.IfFalseMessageNodeFactory;
import som.interpreter.nodes.specialized.IfTrueIfFalseMessageNodeFactory;
import som.interpreter.nodes.specialized.IfTrueMessageNodeFactory;
import som.interpreter.nodes.specialized.IntToByDoMessageNodeFactory;
import som.interpreter.nodes.specialized.IntToDoMessageNodeFactory;
import som.interpreter.nodes.specialized.WhileWithStaticBlocksNode.WhileFalseStaticBlocksNode;
import som.interpreter.nodes.specialized.WhileWithStaticBlocksNode.WhileTrueStaticBlocksNode;
import som.primitives.ArrayPrimsFactory.AtPrimFactory;
import som.primitives.ArrayPrimsFactory.NewPrimFactory;
import som.primitives.BlockPrimsFactory.ValueNonePrimFactory;
import som.primitives.BlockPrimsFactory.ValueOnePrimFactory;
import som.primitives.EqualsEqualsPrimFactory;
import som.primitives.EqualsPrimFactory;
import som.primitives.LengthPrimFactory;
import som.primitives.arithmetic.AdditionPrimFactory;
import som.primitives.arithmetic.BitXorPrimFactory;
import som.primitives.arithmetic.DividePrimFactory;
import som.primitives.arithmetic.DoubleDivPrimFactory;
import som.primitives.arithmetic.LessThanOrEqualPrimFactory;
import som.primitives.arithmetic.LessThanPrimFactory;
import som.primitives.arithmetic.LogicAndPrimFactory;
import som.primitives.arithmetic.ModuloPrimFactory;
import som.primitives.arithmetic.MultiplicationPrimFactory;
import som.primitives.arithmetic.SubtractionPrimFactory;
import som.vm.Universe;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;


public final class MessageSendNode {

  public static AbstractMessageSendNode create(final SSymbol selector,
      final ExpressionNode[] arguments) {
    return new UninitializedMessageSendNode(selector, arguments);
  }

  @NodeInfo(shortName = "send")
  public abstract static class AbstractMessageSendNode extends ExpressionNode
      implements PreevaluatedExpression {

    @Children protected final ExpressionNode[]     argumentNodes;

    protected AbstractMessageSendNode(final ExpressionNode[] arguments) {
      this.argumentNodes = arguments;
    }

    @Override
    public final Object executeGeneric(final VirtualFrame frame) {
      Object[] arguments = evaluateArguments(frame);
      return executePreEvaluated(frame, arguments);
    }

    @ExplodeLoop
    private Object[] evaluateArguments(final VirtualFrame frame) {
      Object[] arguments = new Object[argumentNodes.length];
      for (int i = 0; i < argumentNodes.length; i++) {
        arguments[i] = argumentNodes[i].executeGeneric(frame);
      }
      return arguments;
    }

    @Override
    public final void executeVoid(final VirtualFrame frame) {
      executeGeneric(frame);
    }
  }

  private static final class UninitializedMessageSendNode
      extends AbstractMessageSendNode {

    private final SSymbol selector;

    protected UninitializedMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments) {
      super(arguments);
      this.selector = selector;
    }

    @Override
    public Object executePreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      return specialize(arguments).executePreEvaluated(frame, arguments);
    }

    private PreevaluatedExpression specialize(final Object[] arguments) {
      TruffleCompiler.transferToInterpreterAndInvalidate("Specialize Message Node");

      // first option is a super send, super sends are treated specially because
      // the receiver class is lexically determined
      if (argumentNodes[0] instanceof ISuperReadNode) {
        GenericMessageSendNode node = new GenericMessageSendNode(selector,
            argumentNodes, SuperDispatchNode.create(selector,
                (ISuperReadNode) argumentNodes[0]));
        return replace(node);
      }

      // We treat super sends separately for simplicity, might not be the
      // optimal solution, especially in cases were the knowledge of the
      // receiver class also allows us to do more specific things, but for the
      // moment  we will leave it at this.
      // TODO: revisit, and also do more specific optimizations for super sends.


      // let's organize the specializations by number of arguments
      // perhaps not the best, but one simple way to just get some order into
      // the chaos.

      switch (argumentNodes.length) {
        case  1: return specializeUnary(arguments);
        case  2: return specializeBinary(arguments);
        case  3: return specializeTernary(arguments);
        case  4: return specializeQuaternary(arguments);
      }

      return makeGenericSend();
    }

    private GenericMessageSendNode makeGenericSend() {
      GenericMessageSendNode send = new GenericMessageSendNode(selector,
          argumentNodes,
          new UninitializedDispatchNode(selector, Universe.current()));
      return replace(send);
    }

    private PreevaluatedExpression specializeUnary(final Object[] args) {
      Object receiver = args[0];
      switch (selector.getString()) {
        // eagerly but causious:
        case "value":
          if (receiver instanceof SBlock) {
            return replace(new EagerUnaryPrimitiveNode(selector,
                argumentNodes[0],
                ValueNonePrimFactory.create(null)));
          }
        case "length":
          if (receiver instanceof SArray) {
            return replace(new EagerUnaryPrimitiveNode(selector,
                argumentNodes[0],
                LengthPrimFactory.create(null)));
          }
      }
      return makeGenericSend();
    }

    private PreevaluatedExpression specializeBinary(final Object[] arguments) {
      switch (selector.getString()) {
        case "whileTrue:": {
          if (argumentNodes[1] instanceof BlockNode &&
              argumentNodes[0] instanceof BlockNode) {
            BlockNode argBlockNode = (BlockNode) argumentNodes[1];
            SBlock    argBlock     = (SBlock)    arguments[1];
            return replace(new WhileTrueStaticBlocksNode(
                (BlockNode) argumentNodes[0], argBlockNode,
                (SBlock) arguments[0],
                argBlock, Universe.current()));
          }
          break; // use normal send
        }
        case "whileFalse:":
          if (argumentNodes[1] instanceof BlockNode &&
              argumentNodes[0] instanceof BlockNode) {
            BlockNode argBlockNode = (BlockNode) argumentNodes[1];
            SBlock    argBlock     = (SBlock)    arguments[1];
            return replace(new WhileFalseStaticBlocksNode(
                (BlockNode) argumentNodes[0], argBlockNode,
                (SBlock) arguments[0], argBlock, Universe.current()));
          }
          break; // use normal send
        case "ifTrue:":
          return replace(IfTrueMessageNodeFactory.create(arguments[0],
              arguments[1],
              Universe.current(), argumentNodes[0], argumentNodes[1]));
        case "ifFalse:":
          return replace(IfFalseMessageNodeFactory.create(arguments[0],
              arguments[1],
              Universe.current(), argumentNodes[0], argumentNodes[1]));

        // TODO: find a better way for primitives, use annotation or something
        case "<":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              LessThanPrimFactory.create(null, null)));
        case "<=":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              LessThanOrEqualPrimFactory.create(null, null)));
        case "+":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              AdditionPrimFactory.create(null, null)));
        case "value:":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              ValueOnePrimFactory.create(null, null)));
        case "-":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              SubtractionPrimFactory.create(null, null)));
        case "*":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              MultiplicationPrimFactory.create(null, null)));
        case "=":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              EqualsPrimFactory.create(null, null)));
        case "==":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              EqualsEqualsPrimFactory.create(null, null)));
        case "bitXor:":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              BitXorPrimFactory.create(null, null)));
        case "//":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              DoubleDivPrimFactory.create(null, null)));
        case "%":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              ModuloPrimFactory.create(null, null)));
        case "/":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              DividePrimFactory.create(null, null)));
        case "&":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              LogicAndPrimFactory.create(null, null)));

        // eagerly but causious:
        case "at:":
          if (arguments[0] instanceof SArray) {
            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1],
                AtPrimFactory.create(null, null)));
          }
        case "new:":
          if (arguments[0] instanceof SClass) {
            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1],
                NewPrimFactory.create(null, null)));
          }
      }

      return makeGenericSend();
    }

    private PreevaluatedExpression specializeTernary(final Object[] arguments) {
      switch (selector.getString()) {
        case "ifTrue:ifFalse:":
          return replace(IfTrueIfFalseMessageNodeFactory.create(arguments[0],
              arguments[1], arguments[2], Universe.current(), argumentNodes[0],
              argumentNodes[1], argumentNodes[2]));
        case "to:do:":
          if (TypesGen.TYPES.isImplicitInteger(arguments[0]) &&
              (TypesGen.TYPES.isImplicitInteger(arguments[1]) ||
                  TypesGen.TYPES.isImplicitDouble(arguments[1])) &&
              TypesGen.TYPES.isSBlock(arguments[2])) {
            return replace(IntToDoMessageNodeFactory.create(this,
                (SBlock) arguments[2], argumentNodes[0], argumentNodes[1],
                argumentNodes[2]));
          }
          break;
      }
      return makeGenericSend();
    }

    private PreevaluatedExpression specializeQuaternary(
        final Object[] arguments) {
      switch (selector.getString()) {
        case "to:by:do:":
          return replace(IntToByDoMessageNodeFactory.create(this,
              (SBlock) arguments[3], argumentNodes[0], argumentNodes[1],
              argumentNodes[2], argumentNodes[3]));
      }
      return makeGenericSend();
    }
  }

  public static final class GenericMessageSendNode
      extends AbstractMessageSendNode {

    private final SSymbol selector;

    public static GenericMessageSendNode create(final SSymbol selector,
        final ExpressionNode[] argumentNodes) {
      return new GenericMessageSendNode(selector, argumentNodes,
          new UninitializedDispatchNode(selector, Universe.current()));
    }

    @Child private AbstractDispatchNode dispatchNode;

    private GenericMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments,
        final AbstractDispatchNode dispatchNode) {
      super(arguments);
      this.selector = selector;
      this.dispatchNode = dispatchNode;
    }

    @Override
    public Object executePreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      return dispatchNode.executeDispatch(frame, arguments);
    }

    @SlowPath
    public AbstractDispatchNode getDispatchListHead() {
      return dispatchNode;
    }

    @SlowPath
    public void adoptNewDispatchListHead(final AbstractDispatchNode newHead) {
      dispatchNode = insert(newHead);
    }

    @SlowPath
    public void replaceDispatchListHead(
        final GenericDispatchNode replacement) {
      dispatchNode.replace(replacement);
    }

    @Override
    public String toString() {
      return "GMsgSend(" + selector.getString() + ")";
    }
  }
}
