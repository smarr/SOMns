package som.interpreter.nodes;

import som.compiler.AccessModifier;
import som.interpreter.TruffleCompiler;
import som.interpreter.TypesGen;
import som.interpreter.actors.SPromise;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.DispatchChain.Cost;
import som.interpreter.nodes.dispatch.GenericDispatchNode;
import som.interpreter.nodes.dispatch.LexicallyBoundDispatchNode;
import som.interpreter.nodes.dispatch.SuperDispatchNode;
import som.interpreter.nodes.dispatch.UninitializedDispatchNode;
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.nary.EagerBinaryPrimitiveNode;
import som.interpreter.nodes.nary.EagerTernaryPrimitiveNode;
import som.interpreter.nodes.nary.EagerUnaryPrimitiveNode;
import som.interpreter.nodes.specialized.AndMessageNodeFactory;
import som.interpreter.nodes.specialized.AndMessageNodeFactory.AndBoolMessageNodeFactory;
import som.interpreter.nodes.specialized.IfMessageNodeGen;
import som.interpreter.nodes.specialized.IfTrueIfFalseMessageNodeGen;
import som.interpreter.nodes.specialized.IntDownToDoMessageNodeGen;
import som.interpreter.nodes.specialized.IntToByDoMessageNodeGen;
import som.interpreter.nodes.specialized.IntToDoMessageNodeGen;
import som.interpreter.nodes.specialized.NotMessageNodeFactory;
import som.interpreter.nodes.specialized.OrMessageNodeGen;
import som.interpreter.nodes.specialized.OrMessageNodeGen.OrBoolMessageNodeGen;
import som.interpreter.nodes.specialized.whileloops.WhileWithDynamicBlocksNode;
import som.interpreter.nodes.specialized.whileloops.WhileWithStaticBlocksNode.WhileFalseStaticBlocksNode;
import som.interpreter.nodes.specialized.whileloops.WhileWithStaticBlocksNode.WhileTrueStaticBlocksNode;
import som.primitives.BlockPrimsFactory.ValueNonePrimFactory;
import som.primitives.BlockPrimsFactory.ValueOnePrimFactory;
import som.primitives.BlockPrimsFactory.ValueTwoPrimFactory;
import som.primitives.DoublePrimsFactory.PositiveInfinityPrimFactory;
import som.primitives.DoublePrimsFactory.RoundPrimFactory;
import som.primitives.EqualsEqualsPrimFactory;
import som.primitives.EqualsPrimFactory;
import som.primitives.IntegerPrimsFactory.AbsPrimNodeGen;
import som.primitives.IntegerPrimsFactory.As32BitSignedValueFactory;
import som.primitives.IntegerPrimsFactory.As32BitUnsignedValueFactory;
import som.primitives.IntegerPrimsFactory.LeftShiftPrimFactory;
import som.primitives.IntegerPrimsFactory.MaxIntPrimNodeGen;
import som.primitives.IntegerPrimsFactory.ToPrimNodeGen;
import som.primitives.IntegerPrimsFactory.UnsignedRightShiftPrimFactory;
import som.primitives.MethodPrimsFactory.InvokeOnPrimFactory;
import som.primitives.ObjectPrimsFactory.IsNilNodeGen;
import som.primitives.ObjectPrimsFactory.IsValueFactory;
import som.primitives.ObjectPrimsFactory.NotNilNodeGen;
import som.primitives.SizeAndLengthPrimFactory;
import som.primitives.StringPrimsFactory.SubstringPrimFactory;
import som.primitives.SystemPrims;
import som.primitives.SystemPrimsFactory.TicksPrimFactory;
import som.primitives.UnequalsPrimFactory;
import som.primitives.actors.ActorClasses;
import som.primitives.actors.CreateActorPrimFactory;
import som.primitives.actors.PromisePrimsFactory.CreatePromisePairPrimFactory;
import som.primitives.actors.PromisePrimsFactory.WhenResolvedPrimFactory;
import som.primitives.arithmetic.AdditionPrimFactory;
import som.primitives.arithmetic.DividePrimFactory;
import som.primitives.arithmetic.DoubleDivPrimFactory;
import som.primitives.arithmetic.ExpPrimFactory;
import som.primitives.arithmetic.GreaterThanOrEqualPrimNodeGen;
import som.primitives.arithmetic.GreaterThanPrimNodeGen;
import som.primitives.arithmetic.LessThanOrEqualPrimNodeGen;
import som.primitives.arithmetic.LessThanPrimFactory;
import som.primitives.arithmetic.LogPrimFactory;
import som.primitives.arithmetic.ModuloPrimFactory;
import som.primitives.arithmetic.MultiplicationPrimFactory;
import som.primitives.arithmetic.RemainderPrimFactory;
import som.primitives.arithmetic.SinPrimFactory;
import som.primitives.arithmetic.SqrtPrimFactory;
import som.primitives.arithmetic.SubtractionPrimFactory;
import som.primitives.arrays.AtPrimFactory;
import som.primitives.arrays.AtPutPrimFactory;
import som.primitives.arrays.CopyPrimNodeGen;
import som.primitives.arrays.DoIndexesPrimFactory;
import som.primitives.arrays.DoPrimFactory;
import som.primitives.arrays.NewImmutableArrayNodeGen;
import som.primitives.arrays.NewPrimFactory;
import som.primitives.arrays.PutAllNodeFactory;
import som.primitives.arrays.ToArgumentsArrayNodeGen;
import som.primitives.bitops.BitAndPrimFactory;
import som.primitives.bitops.BitXorPrimFactory;
import som.vm.NotYetImplementedException;
import som.vm.constants.Classes;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.source.SourceSection;


public final class MessageSendNode {

  public static AbstractMessageSendNode createMessageSend(final SSymbol selector,
      final ExpressionNode[] arguments, final SourceSection source) {
    return new UninitializedMessageSendNode(selector, arguments, source);
  }

  public static AbstractMessageSendNode adaptSymbol(final SSymbol newSelector,
      final AbstractMessageSendNode node) {
    assert node instanceof UninitializedMessageSendNode;
    return new UninitializedMessageSendNode(newSelector, node.argumentNodes,
        node.getSourceSection());
  }

  public static AbstractMessageSendNode createForPerformNodes(final SSymbol selector) {
    return new UninitializedSymbolSendNode(selector, null);
  }

  public static GenericMessageSendNode createGeneric(final SSymbol selector,
      final ExpressionNode[] argumentNodes, final SourceSection source) {
    if (argumentNodes != null && argumentNodes[0] instanceof ISpecialSend) {
      throw new NotYetImplementedException();
    } else {
      return new GenericMessageSendNode(selector, argumentNodes,
          new UninitializedDispatchNode(selector, AccessModifier.PUBLIC), source);
    }
  }

  public abstract static class AbstractMessageSendNode extends ExpressionNode
      implements PreevaluatedExpression {

    @Children protected final ExpressionNode[] argumentNodes;

    protected AbstractMessageSendNode(final ExpressionNode[] arguments,
        final SourceSection source) {
      super(source);
      this.argumentNodes = arguments;
    }

    public boolean isSpecialSend() {
      return argumentNodes[0] instanceof ISpecialSend;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
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
  }

  public abstract static class AbstractUninitializedMessageSendNode
      extends AbstractMessageSendNode {

    protected final SSymbol selector;

    protected AbstractUninitializedMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments,
        final SourceSection source) {
      super(arguments, source);
      this.selector = selector;
    }

    @Override
    public String toString() {
      return "UninitMsgSend(" + selector.toString() + ")";
    }

    public SSymbol getSelector() {
      return selector;
    }

    @Override
    public final Object executeGeneric(final VirtualFrame frame) {
      // This is a branch never taken, none of the code here should be compiled.
      CompilerDirectives.transferToInterpreter();
      return super.executeGeneric(frame);
    }

    @Override
    public final Object doPreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      // This is a branch never taken, none of the code here should be compiled.
      CompilerDirectives.transferToInterpreter();
      return specialize(arguments).
          doPreEvaluated(frame, arguments);
    }

    private PreevaluatedExpression specialize(final Object[] arguments) {
      TruffleCompiler.transferToInterpreterAndInvalidate("Specialize Message Node");

      // let's organize the specializations by number of arguments
      // perhaps not the best, but simple
      switch (argumentNodes.length) {
        case  1: return specializeUnary(arguments);
        case  2: return specializeBinary(arguments);
        case  3: return specializeTernary(arguments);
        case  4: return specializeQuaternary(arguments);
      }
      return makeSend();
    }

    protected PreevaluatedExpression makeSend() {
      // first option is a super send, super sends are treated specially because
      // the receiver class is lexically determined
      if (isSpecialSend()) {
        return makeSpecialSend();
      }
      return makeOrdenarySend();
    }

    protected abstract PreevaluatedExpression makeSpecialSend();

    private GenericMessageSendNode makeOrdenarySend() {
      GenericMessageSendNode send = new GenericMessageSendNode(selector,
          argumentNodes,
          new UninitializedDispatchNode(selector, AccessModifier.PUBLIC),
          getSourceSection());
      return replace(send);
    }

    protected PreevaluatedExpression specializeUnary(final Object[] args) {
      Object receiver = args[0];
      switch (selector.getString()) {
        // eagerly but cautious:
        case "size":
          if (receiver instanceof SArray) {
            return replace(new EagerUnaryPrimitiveNode(selector,
                argumentNodes[0], SizeAndLengthPrimFactory.create(null)));
          }
          break;
        case "length":
          if (receiver instanceof String) {
            return replace(new EagerUnaryPrimitiveNode(selector,
                argumentNodes[0], SizeAndLengthPrimFactory.create(null)));
          }
          break;
        case "value":
          if (receiver instanceof SBlock || receiver instanceof Boolean) {
            return replace(new EagerUnaryPrimitiveNode(selector,
                argumentNodes[0], ValueNonePrimFactory.create(null)));
          }
          break;
        case "not":
          if (receiver instanceof Boolean) {
            return replace(new EagerUnaryPrimitiveNode(selector,
                argumentNodes[0], NotMessageNodeFactory.create(getSourceSection(), null)));
          }
          break;
        case "abs":
          if (receiver instanceof Long) {
            return replace(new EagerUnaryPrimitiveNode(selector,
                argumentNodes[0], AbsPrimNodeGen.create(null)));
          }
          break;
        case "copy":
          if (receiver instanceof SArray) {
            return replace(new EagerUnaryPrimitiveNode(selector,
                argumentNodes[0], CopyPrimNodeGen.create(null)));
          }
          break;
        case "PositiveInfinity":
          if (receiver == Classes.doubleClass) {
            // don't need to protect this with an eager wrapper
            return replace(PositiveInfinityPrimFactory.create(argumentNodes[0]));
          }
          break;
        case "round":
          if (receiver instanceof Double) {
            return replace(new EagerUnaryPrimitiveNode(selector,
                argumentNodes[0], RoundPrimFactory.create(null)));
          }
          break;
        case "as32BitSignedValue":
          if (receiver instanceof Long) {
            return replace(new EagerUnaryPrimitiveNode(selector,
                argumentNodes[0], As32BitSignedValueFactory.create(null)));
          }
          break;
        case "as32BitUnsignedValue":
          if (receiver instanceof Long) {
            return replace(new EagerUnaryPrimitiveNode(selector,
                argumentNodes[0], As32BitUnsignedValueFactory.create(null)));
          }
          break;
        case "sin":
          if (receiver instanceof Double) {
            return replace(new EagerUnaryPrimitiveNode(selector,
                argumentNodes[0], SinPrimFactory.create(null)));
          }
          break;
        case "exp":
          if (receiver instanceof Double) {
            return replace(new EagerUnaryPrimitiveNode(selector,
                argumentNodes[0], ExpPrimFactory.create(null)));
          }
          break;
        case "log":
          if (receiver instanceof Double) {
            return replace(new EagerUnaryPrimitiveNode(selector,
                argumentNodes[0], LogPrimFactory.create(null)));
          }
          break;
        case "sqrt":
          if (receiver instanceof Number) {
            return replace(new EagerUnaryPrimitiveNode(selector,
                argumentNodes[0], SqrtPrimFactory.create(null)));
          }
          break;
        case "isNil":
          return replace(IsNilNodeGen.create(argumentNodes[0]));
        case "notNil":
          return replace(NotNilNodeGen.create(argumentNodes[0]));
        case "ticks":
          if (receiver == SystemPrims.SystemModule) {
            return replace(TicksPrimFactory.create(argumentNodes[0]));
          }
          break;
        case "createPromisePair":
          if (receiver == ActorClasses.ActorModule) {
            return replace(CreatePromisePairPrimFactory.create(argumentNodes[0]));
          }
          break;
      }
      return makeSend();
    }

    protected PreevaluatedExpression specializeBinary(final Object[] arguments) {
      switch (selector.getString()) {
        case "at:":
          if (arguments[0] instanceof SArray) {
            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1],
                AtPrimFactory.create(null, null)));
          }
          break;
        case "new:":
          if (arguments[0] instanceof SClass && ((SClass) arguments[0]).isArray()) {
            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1],
                NewPrimFactory.create(null, null)));
          }
          break;
        case "doIndexes:":
          if (arguments[0] instanceof SArray) {
            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1],
                DoIndexesPrimFactory.create(null, null)));
          }
          break;
        case "do:":
          if (arguments[0] instanceof SArray) {
            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1],
                DoPrimFactory.create(null, null)));
          }
          break;
        case "putAll:":
          return replace(new EagerBinaryPrimitiveNode(selector,
                argumentNodes[0], argumentNodes[1],
                PutAllNodeFactory.create(null, null, SizeAndLengthPrimFactory.create(null))));
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
              return replace(OrMessageNodeGen.create((SBlock) arguments[1],
                  getSourceSection(),
                  argumentNodes[0], argumentNodes[1]));
            } else if (arguments[1] instanceof Boolean) {
              return replace(OrBoolMessageNodeGen.create(
                  getSourceSection(),
                  argumentNodes[0], argumentNodes[1]));
            }
          }
          break;

        case "value:":
          if (arguments[0] instanceof SBlock) {
            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1],
                ValueOnePrimFactory.create(null, null)));
          }
          break;

        case "ifTrue:":
          return replace(IfMessageNodeGen.create(true, getSourceSection(),
              argumentNodes[0], argumentNodes[1]));
        case "ifFalse:":
          return replace(IfMessageNodeGen.create(false, getSourceSection(),
              argumentNodes[0], argumentNodes[1]));
        case "to:":
          if (arguments[0] instanceof Long) {
            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1],
                ToPrimNodeGen.create(null, null)));
          }
          break;

        case "createActorFromValue:": {
          if (arguments[0] == ActorClasses.ActorModule) {
            return replace(CreateActorPrimFactory.create(argumentNodes[0],
                argumentNodes[1], IsValueFactory.create(null)));
          }
          break;
        }
        case "whenResolved:": {
          if (arguments[0] instanceof SPromise) {
            return replace(new EagerBinaryPrimitiveNode(selector,
                argumentNodes[0], argumentNodes[1],
                WhenResolvedPrimFactory.create(null, null)));
          }
          break;
        }

        // TODO: find a better way for primitives, use annotation or something
        case "<":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              LessThanPrimFactory.create(null, null)));
        case "<=":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              LessThanOrEqualPrimNodeGen.create(null, null)));
        case ">":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              GreaterThanPrimNodeGen.create(null, null)));
        case ">=":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              GreaterThanOrEqualPrimNodeGen.create(null, null)));
        case "+":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              AdditionPrimFactory.create(null, null)));
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
// TODO: this is not a correct primitive, new an UnequalsUnequalsPrim...
//        case "~=":
//          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
//              argumentNodes[1],
//              UnequalsPrimFactory.create(null, null)));
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
        case "rem:":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              RemainderPrimFactory.create(null, null)));
        case "/":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              DividePrimFactory.create(null, null)));
        case "&":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              BitAndPrimFactory.create(null, null)));

        // eagerly but cautious:
        case "<<":
          if (arguments[0] instanceof Long) {
            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1],
                LeftShiftPrimFactory.create(null, null)));
          }
          break;
        case ">>>":
          if (arguments[0] instanceof Long) {
            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1],
                UnsignedRightShiftPrimFactory.create(null, null)));
          }
          break;
        case "max:":
          if (arguments[0] instanceof Long) {
            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1],
                MaxIntPrimNodeGen.create(null, null)));
          }
          break;
      }
      return makeSend();
    }

    protected PreevaluatedExpression specializeTernary(final Object[] arguments) {
      switch (selector.getString()) {
        case "at:put:":
          if (arguments[0] instanceof SArray) {
            return replace(new EagerTernaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1], argumentNodes[2],
                AtPutPrimFactory.create(null, null, null)));
          }
          break;
        case "ifTrue:ifFalse:":
          return replace(IfTrueIfFalseMessageNodeGen.create(arguments[0],
              arguments[1], arguments[2], argumentNodes[0],
              argumentNodes[1], argumentNodes[2]));
        case "to:do:":
          if (TypesGen.isLong(arguments[0]) &&
              (TypesGen.isLong(arguments[1]) ||
                  TypesGen.isDouble(arguments[1])) &&
              TypesGen.isSBlock(arguments[2])) {
            return replace(IntToDoMessageNodeGen.create(argumentNodes[0],
                argumentNodes[1], argumentNodes[2]));
          }
          break;
        case "downTo:do:":
          if (TypesGen.isLong(arguments[0]) &&
              (TypesGen.isLong(arguments[1]) ||
                  TypesGen.isDouble(arguments[1])) &&
              TypesGen.isSBlock(arguments[2])) {
            return replace(IntDownToDoMessageNodeGen.create(this,
                (SBlock) arguments[2], argumentNodes[0], argumentNodes[1],
                argumentNodes[2]));
          }
          break;
        case "substringFrom:to:":
          if (arguments[0] instanceof String) {
            return replace(new EagerTernaryPrimitiveNode(selector,
                argumentNodes[0], argumentNodes[1], argumentNodes[2],
                SubstringPrimFactory.create(null, null, null)));
          }
          break;
        case "invokeOn:with:":
          return replace(InvokeOnPrimFactory.create(
              argumentNodes[0], argumentNodes[1], argumentNodes[2],
              ToArgumentsArrayNodeGen.create(null, null)));
        case "value:with:":
          if (arguments[0] instanceof SBlock) {
            return replace(new EagerTernaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1], argumentNodes[2],
                ValueTwoPrimFactory.create(null, null, null)));
          }
          break;
        case "new:withAll:":
          if (arguments[0] == Classes.valueArrayClass) {
            return replace(new EagerTernaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1], argumentNodes[2],
                NewImmutableArrayNodeGen.create(null, null, null)));
          }
      }
      return makeSend();
    }

    protected PreevaluatedExpression specializeQuaternary(
        final Object[] arguments) {
      switch (selector.getString()) {
        case "to:by:do:":
          return replace(IntToByDoMessageNodeGen.create(this,
              (SBlock) arguments[3], argumentNodes[0], argumentNodes[1],
              argumentNodes[2], argumentNodes[3]));
      }
      return makeSend();
    }
  }

  private static final class UninitializedMessageSendNode
      extends AbstractUninitializedMessageSendNode {

    protected UninitializedMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments,
        final SourceSection source) {
      super(selector, arguments, source);
    }

    @Override
    protected PreevaluatedExpression makeSpecialSend() {
      ISpecialSend rcvrNode = (ISpecialSend) argumentNodes[0];
      AbstractDispatchNode dispatch;

      if (rcvrNode.isSuperSend()) {
        dispatch = SuperDispatchNode.create(selector, (ISuperReadNode) rcvrNode);
      } else {
        dispatch = new LexicallyBoundDispatchNode(selector, rcvrNode.getEnclosingMixinId());
      }

      GenericMessageSendNode node = new GenericMessageSendNode(selector,
        argumentNodes, dispatch, getSourceSection());
      return replace(node);
    }
  }

  private static final class UninitializedSymbolSendNode
    extends AbstractUninitializedMessageSendNode {

    protected UninitializedSymbolSendNode(final SSymbol selector, final SourceSection source) {
      super(selector, new ExpressionNode[0], source);
    }

    @Override
    public boolean isSpecialSend() {
      return false;
    }

    @Override
    protected PreevaluatedExpression makeSpecialSend() {
      // should never be reached with isSuperSend() returning always false
      throw new RuntimeException("A symbol send should never be a special send.");
    }

    @Override
    protected PreevaluatedExpression specializeBinary(final Object[] arguments) {
      switch (selector.getString()) {
        case "whileTrue:": {
          if (arguments[1] instanceof SBlock && arguments[0] instanceof SBlock) {
            SBlock argBlock = (SBlock) arguments[1];
            return replace(new WhileWithDynamicBlocksNode((SBlock) arguments[0],
                argBlock, true, getSourceSection()));
          }
          break;
        }
        case "whileFalse:":
          if (arguments[1] instanceof SBlock && arguments[0] instanceof SBlock) {
            SBlock    argBlock     = (SBlock)    arguments[1];
            return replace(new WhileWithDynamicBlocksNode(
                (SBlock) arguments[0], argBlock, false, getSourceSection()));
          }
          break; // use normal send
      }

      return super.specializeBinary(arguments);
    }
  }

  public static final class GenericMessageSendNode
      extends AbstractMessageSendNode {

    private final SSymbol selector;

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

    public AbstractDispatchNode getDispatchListHead() {
      return dispatchNode;
    }

    public void adoptNewDispatchListHead(final AbstractDispatchNode newHead) {
      CompilerAsserts.neverPartOfCompilation();
      dispatchNode = insert(newHead);
    }

    public void replaceDispatchListHead(
        final GenericDispatchNode replacement) {
      CompilerAsserts.neverPartOfCompilation();
      dispatchNode.replace(replacement);
    }

    @Override
    public String toString() {
      String file = "";
      if (getSourceSection() != null) {
        file = " " + getSourceSection().getSource().getName();
        file += ":" + getSourceSection().getStartLine();
        file += ":" + getSourceSection().getStartColumn();
      }

      return "GMsgSend(" + selector.getString() + file + ")";
    }

    @Override
    public NodeCost getCost() {
      return Cost.getCost(dispatchNode);
    }
  }
}
