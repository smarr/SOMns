package som.interpreter.nodes;

import som.interpreter.TruffleCompiler;
import som.interpreter.TypesGen;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.DispatchChain.Cost;
import som.interpreter.nodes.dispatch.GenericDispatchNode;
import som.interpreter.nodes.dispatch.SuperDispatchNode;
import som.interpreter.nodes.dispatch.UninitializedDispatchNode;
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.nary.EagerBinaryPrimitiveNode;
import som.interpreter.nodes.nary.EagerTernaryPrimitiveNode;
import som.interpreter.nodes.nary.EagerUnaryPrimitiveNode;
import som.interpreter.nodes.specialized.AndMessageNodeFactory;
import som.interpreter.nodes.specialized.AndMessageNodeFactory.AndBoolMessageNodeFactory;
import som.interpreter.nodes.specialized.IfFalseMessageNodeFactory;
import som.interpreter.nodes.specialized.IfTrueIfFalseMessageNodeFactory;
import som.interpreter.nodes.specialized.IfTrueMessageNodeFactory;
import som.interpreter.nodes.specialized.IntDownToDoMessageNodeFactory;
import som.interpreter.nodes.specialized.IntToByDoMessageNodeFactory;
import som.interpreter.nodes.specialized.IntToDoMessageNodeFactory;
import som.interpreter.nodes.specialized.NotMessageNodeFactory;
import som.interpreter.nodes.specialized.OrMessageNodeFactory;
import som.interpreter.nodes.specialized.OrMessageNodeFactory.OrBoolMessageNodeFactory;
import som.interpreter.nodes.specialized.WhileWithDynamicBlocksNode.WhileFalseDynamicBlocksNode;
import som.interpreter.nodes.specialized.WhileWithDynamicBlocksNode.WhileTrueDynamicBlocksNode;
import som.interpreter.nodes.specialized.WhileWithStaticBlocksNode.WhileFalseStaticBlocksNode;
import som.interpreter.nodes.specialized.WhileWithStaticBlocksNode.WhileTrueStaticBlocksNode;
import som.primitives.ArrayPrimsFactory.AtPrimFactory;
import som.primitives.ArrayPrimsFactory.AtPutPrimFactory;
import som.primitives.ArrayPrimsFactory.DoIndexesPrimFactory;
import som.primitives.ArrayPrimsFactory.DoPrimFactory;
import som.primitives.ArrayPrimsFactory.NewPrimFactory;
import som.primitives.BlockPrimsFactory.ValueNonePrimFactory;
import som.primitives.BlockPrimsFactory.ValueOnePrimFactory;
import som.primitives.EqualsEqualsPrimFactory;
import som.primitives.EqualsPrimFactory;
import som.primitives.IntegerPrimsFactory.LeftShiftPrimFactory;
import som.primitives.LengthPrimFactory;
import som.primitives.UnequalsPrimFactory;
import som.primitives.arithmetic.AdditionPrimFactory;
import som.primitives.arithmetic.BitXorPrimFactory;
import som.primitives.arithmetic.DividePrimFactory;
import som.primitives.arithmetic.DoubleDivPrimFactory;
import som.primitives.arithmetic.GreaterThanPrimFactory;
import som.primitives.arithmetic.LessThanOrEqualPrimFactory;
import som.primitives.arithmetic.LessThanPrimFactory;
import som.primitives.arithmetic.LogicAndPrimFactory;
import som.primitives.arithmetic.ModuloPrimFactory;
import som.primitives.arithmetic.MultiplicationPrimFactory;
import som.primitives.arithmetic.SubtractionPrimFactory;
import som.vm.constants.Classes;
import som.vmobjects.SBlock;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;


public final class MessageSendNode {

  public static AbstractMessageSendNode create(final SSymbol selector,
      final ExpressionNode[] arguments, final SourceSection source) {
    return new UninitializedMessageSendNode(selector, arguments, source);
  }

  public static AbstractMessageSendNode createForPerformNodes(final SSymbol selector) {
    return new UninitializedSymbolSendNode(selector, null);
  }

  @NodeInfo(shortName = "send")
  public abstract static class AbstractMessageSendNode extends ExpressionNode
      implements PreevaluatedExpression {

    @Children protected final ExpressionNode[] argumentNodes;

    protected AbstractMessageSendNode(final ExpressionNode[] arguments,
        final SourceSection source) {
      super(source);
      this.argumentNodes = arguments;
    }

    @Override
    public final Object executeGeneric(final VirtualFrame frame) {
      Object[] arguments = evaluateArguments(frame);
      return doPreEvaluated(frame, arguments);
    }

    @ExplodeLoop
    private Object[] evaluateArguments(final VirtualFrame frame) {
      Object[] arguments = new Object[argumentNodes.length];
      for (int i = 0; i < argumentNodes.length; i++) {
        arguments[i] = argumentNodes[i].executeGeneric(frame);
        assert arguments[i] != null;
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
        final ExpressionNode[] arguments, final SourceSection source) {
      super(arguments, source);
      this.selector = selector;
    }

    @Override
    public Object doPreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      return specialize(arguments).doPreEvaluated(frame, arguments);
    }

    private PreevaluatedExpression specialize(final Object[] arguments) {
      TruffleCompiler.transferToInterpreterAndInvalidate("Specialize Message Node");

      // first option is a super send, super sends are treated specially because
      // the receiver class is lexically determined
      if (argumentNodes[0] instanceof ISuperReadNode) {
        GenericMessageSendNode node = new GenericMessageSendNode(selector,
            argumentNodes, SuperDispatchNode.create(selector,
                (ISuperReadNode) argumentNodes[0]), getSourceSection());
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
          new UninitializedDispatchNode(selector),
          getSourceSection());
      return replace(send);
    }

    private PreevaluatedExpression specializeUnary(final Object[] args) {
      Object receiver = args[0];
      switch (selector.getString()) {
        // eagerly but cautious:
        case "value":
          if (receiver instanceof SBlock) {
            return replace(new EagerUnaryPrimitiveNode(selector,
                argumentNodes[0], ValueNonePrimFactory.create(null)));
          }
          break;
        case "length":
          if (receiver instanceof Object[]) {
            return replace(new EagerUnaryPrimitiveNode(selector,
                argumentNodes[0], LengthPrimFactory.create(null)));
          }
          break;
        case "not":
          if (receiver instanceof Boolean) {
            return replace(new EagerUnaryPrimitiveNode(selector,
                argumentNodes[0], NotMessageNodeFactory.create(getSourceSection(), null)));
          }
          break;
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
                argBlock, getSourceSection()));
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
                (SBlock) arguments[0], argBlock, getSourceSection()));
          }
          break; // use normal send
        case "ifTrue:":
          return replace(IfTrueMessageNodeFactory.create(arguments[0],
              arguments[1], getSourceSection(),
              argumentNodes[0], argumentNodes[1]));
        case "ifFalse:":
          return replace(IfFalseMessageNodeFactory.create(arguments[0],
              arguments[1], getSourceSection(),
              argumentNodes[0], argumentNodes[1]));

        // TODO: find a better way for primitives, use annotation or something
        case "<":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              LessThanPrimFactory.create(null, null)));
        case "<=":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              LessThanOrEqualPrimFactory.create(null, null)));
        case ">":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              GreaterThanPrimFactory.create(null, null)));
        case "+":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              AdditionPrimFactory.create(null, null)));
        case "value:":
          if (arguments[0] instanceof SBlock) {
            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1],
                ValueOnePrimFactory.create(null, null)));
          }
          break;
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
        case "<>":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              UnequalsPrimFactory.create(null, null)));
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
        case "<<":
          if (arguments[0] instanceof Long) {
            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1],
                LeftShiftPrimFactory.create(null, null)));
          }
          break;
        case "at:":
          if (arguments[0] instanceof Object[]) {
            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1],
                AtPrimFactory.create(null, null)));
          }
          break;
        case "new:":
          if (arguments[0] == Classes.arrayClass) {
            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1],
                NewPrimFactory.create(null, null)));
          }
          break;
        case "and:":
        case "&&":
          if (arguments[0] instanceof Boolean) {
            if (argumentNodes[1] instanceof BlockNode) {
              return replace(AndMessageNodeFactory.create((SBlock) arguments[1],
                  getSourceSection(), argumentNodes[0], argumentNodes[1]));
            } else if (arguments[1] instanceof Boolean) {
              return replace(AndBoolMessageNodeFactory.create(getSourceSection(),
                  argumentNodes[0], argumentNodes[1]));
            }
          }
          break;
        case "or:":
        case "||":
          if (arguments[0] instanceof Boolean) {
            if (argumentNodes[1] instanceof BlockNode) {
              return replace(OrMessageNodeFactory.create((SBlock) arguments[1],
                  getSourceSection(),
                  argumentNodes[0], argumentNodes[1]));
            } else if (arguments[1] instanceof Boolean) {
              return replace(OrBoolMessageNodeFactory.create(
                  getSourceSection(),
                  argumentNodes[0], argumentNodes[1]));
            }
          }
          break;

        case "doIndexes:":
          if (arguments[0] instanceof Object[]) {
            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1],
                DoIndexesPrimFactory.create(null, null)));
          }
          break;
        case "do:":
          if (arguments[0] instanceof Object[]) {
            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1],
                DoPrimFactory.create(null, null)));
          }
          break;
      }

      return makeGenericSend();
    }

    private PreevaluatedExpression specializeTernary(final Object[] arguments) {
      switch (selector.getString()) {
        case "ifTrue:ifFalse:":
          return replace(IfTrueIfFalseMessageNodeFactory.create(arguments[0],
              arguments[1], arguments[2], argumentNodes[0],
              argumentNodes[1], argumentNodes[2]));
        case "to:do:":
          if (TypesGen.TYPES.isLong(arguments[0]) &&
              (TypesGen.TYPES.isLong(arguments[1]) ||
                  TypesGen.TYPES.isDouble(arguments[1])) &&
              TypesGen.TYPES.isSBlock(arguments[2])) {
            return replace(IntToDoMessageNodeFactory.create(this,
                (SBlock) arguments[2], argumentNodes[0], argumentNodes[1],
                argumentNodes[2]));
          }
          break;
        case "downTo:do:":
          if (TypesGen.TYPES.isLong(arguments[0]) &&
              (TypesGen.TYPES.isLong(arguments[1]) ||
                  TypesGen.TYPES.isDouble(arguments[1])) &&
              TypesGen.TYPES.isSBlock(arguments[2])) {
            return replace(IntDownToDoMessageNodeFactory.create(this,
                (SBlock) arguments[2], argumentNodes[0], argumentNodes[1],
                argumentNodes[2]));
          }
          break;
        case "at:put:":
          if (arguments[0] instanceof Object[]) {
            return replace(new EagerTernaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1], argumentNodes[2],
                AtPutPrimFactory.create(null, null, null)));
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


  private static final class UninitializedSymbolSendNode
    extends AbstractMessageSendNode {

    private final SSymbol selector;

    protected UninitializedSymbolSendNode(final SSymbol selector,
        final SourceSection source) {
      super(new ExpressionNode[0], source);
      this.selector = selector;
    }

    @Override
    public Object doPreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      return specialize(arguments).doPreEvaluated(frame, arguments);
    }

    private PreevaluatedExpression specialize(final Object[] arguments) {
      TruffleCompiler.transferToInterpreterAndInvalidate("Specialize Symbol Send Node");

      switch (arguments.length) {
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
          new UninitializedDispatchNode(selector),
          getSourceSection());
      return replace(send);
    }

    private PreevaluatedExpression specializeUnary(final Object[] args) {
      Object receiver = args[0];
      switch (selector.getString()) {
        // eagerly but cautious:
//        case "value":
//          if (receiver instanceof SBlock) {
//            return replace(new EagerUnaryPrimitiveNode(selector,
//                argumentNodes[0], ValueNonePrimFactory.create(null)));
//          }
//          break;
//        case "length":
//          if (receiver instanceof Object[]) {
//            return replace(new EagerUnaryPrimitiveNode(selector,
//                argumentNodes[0], LengthPrimFactory.create(null)));
//          }
//          break;
//        case "not":
//          if (receiver instanceof Boolean) {
//            return replace(new EagerUnaryPrimitiveNode(selector,
//                argumentNodes[0], NotMessageNodeFactory.create(getSourceSection(), null)));
//          }
//          break;
      }
      return makeGenericSend();
    }

    private PreevaluatedExpression specializeBinary(final Object[] arguments) {
      switch (selector.getString()) {
        case "whileTrue:": {
          if (arguments[1] instanceof SBlock && arguments[0] instanceof SBlock) {
            SBlock argBlock = (SBlock) arguments[1];
            return replace(new WhileTrueDynamicBlocksNode((SBlock) arguments[0],
                argBlock, getSourceSection()));
          }
          break;
        }
        case "whileFalse:":
          if (arguments[1] instanceof SBlock && arguments[0] instanceof SBlock) {
            SBlock    argBlock     = (SBlock)    arguments[1];
            return replace(new WhileFalseDynamicBlocksNode(
                (SBlock) arguments[0], argBlock, getSourceSection()));
          }
          break; // use normal send
//        case "ifTrue:":
//          return replace(IfTrueMessageNodeFactory.create(arguments[0],
//              arguments[1],
//              Universe.current(), getSourceSection(),
//              argumentNodes[0], argumentNodes[1]));
//        case "ifFalse:":
//          return replace(IfFalseMessageNodeFactory.create(arguments[0],
//              arguments[1],
//              Universe.current(), getSourceSection(),
//              argumentNodes[0], argumentNodes[1]));
//
//        // TODO: find a better way for primitives, use annotation or something
//        case "<":
//          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
//              argumentNodes[1],
//              LessThanPrimFactory.create(null, null)));
//        case "<=":
//          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
//              argumentNodes[1],
//              LessThanOrEqualPrimFactory.create(null, null)));
//        case ">":
//          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
//              argumentNodes[1],
//              GreaterThanPrimFactory.create(null, null)));
//        case "+":
//          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
//              argumentNodes[1],
//              AdditionPrimFactory.create(null, null)));
//        case "value:":
//          if (arguments[0] instanceof SBlock) {
//            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
//                argumentNodes[1],
//                ValueOnePrimFactory.create(null, null)));
//          }
//          break;
//        case "-":
//          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
//              argumentNodes[1],
//              SubtractionPrimFactory.create(null, null)));
//        case "*":
//          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
//              argumentNodes[1],
//              MultiplicationPrimFactory.create(null, null)));
//        case "=":
//          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
//              argumentNodes[1],
//              EqualsPrimFactory.create(null, null)));
//        case "==":
//          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
//              argumentNodes[1],
//              EqualsEqualsPrimFactory.create(null, null)));
//        case "bitXor:":
//          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
//              argumentNodes[1],
//              BitXorPrimFactory.create(null, null)));
//        case "//":
//          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
//              argumentNodes[1],
//              DoubleDivPrimFactory.create(null, null)));
//        case "%":
//          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
//              argumentNodes[1],
//              ModuloPrimFactory.create(null, null)));
//        case "/":
//          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
//              argumentNodes[1],
//              DividePrimFactory.create(null, null)));
//        case "&":
//          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
//              argumentNodes[1],
//              LogicAndPrimFactory.create(null, null)));
//
//        // eagerly but causious:
//        case "<<":
//          if (arguments[0] instanceof Long) {
//            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
//                argumentNodes[1],
//                LeftShiftPrimFactory.create(null, null)));
//          }
//          break;
//        case "at:":
//          if (arguments[0] instanceof Object[]) {
//            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
//                argumentNodes[1],
//                AtPrimFactory.create(null, null)));
//          }
//        case "new:":
//          if (arguments[0] == Universe.current().arrayClass) {
//            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
//                argumentNodes[1],
//                NewPrimFactory.create(null, null)));
//          }
//          break;
//        case "and:":
//        case "&&":
//          if (arguments[0] instanceof Boolean) {
//            if (argumentNodes[1] instanceof BlockNode) {
//              return replace(AndMessageNodeFactory.create((SBlock) arguments[1],
//                  getSourceSection(), argumentNodes[0], argumentNodes[1]));
//            } else if (arguments[1] instanceof Boolean) {
//              return replace(AndBoolMessageNodeFactory.create(getSourceSection(),
//                  argumentNodes[0], argumentNodes[1]));
//            }
//          }
//          break;
//        case "or:":
//        case "||":
//          if (arguments[0] instanceof Boolean) {
//            if (argumentNodes[1] instanceof BlockNode) {
//              return replace(OrMessageNodeFactory.create((SBlock) arguments[1],
//                  getSourceSection(),
//                  argumentNodes[0], argumentNodes[1]));
//            } else if (arguments[1] instanceof Boolean) {
//              return replace(OrBoolMessageNodeFactory.create(
//                  getSourceSection(),
//                  argumentNodes[0], argumentNodes[1]));
//            }
//          }
//          break;
      }

      return makeGenericSend();
    }

    private PreevaluatedExpression specializeTernary(final Object[] arguments) {
      switch (selector.getString()) {
//        case "ifTrue:ifFalse:":
//          return replace(IfTrueIfFalseMessageNodeFactory.create(arguments[0],
//              arguments[1], arguments[2], Universe.current(), argumentNodes[0],
//              argumentNodes[1], argumentNodes[2]));
//        case "to:do:":
//          if (TypesGen.TYPES.isLong(arguments[0]) &&
//              (TypesGen.TYPES.isLong(arguments[1]) ||
//                  TypesGen.TYPES.isDouble(arguments[1])) &&
//              TypesGen.TYPES.isSBlock(arguments[2])) {
//            return replace(IntToDoMessageNodeFactory.create(this,
//                (SBlock) arguments[2], argumentNodes[0], argumentNodes[1],
//                argumentNodes[2]));
//          }
//          break;
      }
      return makeGenericSend();
    }

    private PreevaluatedExpression specializeQuaternary(
        final Object[] arguments) {
      switch (selector.getString()) {
//        case "to:by:do:":
//          return replace(IntToByDoMessageNodeFactory.create(this,
//              (SBlock) arguments[3], argumentNodes[0], argumentNodes[1],
//              argumentNodes[2], argumentNodes[3]));
      }
      return makeGenericSend();
    }
  }

  public static final class GenericMessageSendNode
      extends AbstractMessageSendNode {

    private final SSymbol selector;

    public static GenericMessageSendNode create(final SSymbol selector,
        final ExpressionNode[] argumentNodes, final SourceSection source) {
      return new GenericMessageSendNode(selector, argumentNodes,
          new UninitializedDispatchNode(selector), source);
    }

    @Child private AbstractDispatchNode dispatchNode;

    private GenericMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments,
        final AbstractDispatchNode dispatchNode, final SourceSection source) {
      super(arguments, source);
      this.selector = selector;
      this.dispatchNode = dispatchNode;
    }

    @Override
    public Object doPreEvaluated(final VirtualFrame frame,
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

    @Override
    public NodeCost getCost() {
      return Cost.getCost(dispatchNode);
    }
  }
}
