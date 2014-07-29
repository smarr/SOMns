package som.interpreter.nodes;

import som.interpreter.SArguments;
import som.interpreter.TruffleCompiler;
import som.interpreter.TypesGen;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.DispatchChain.Cost;
import som.interpreter.nodes.dispatch.GenericDispatchNode;
import som.interpreter.nodes.dispatch.SuperDispatchNode;
import som.interpreter.nodes.dispatch.UninitializedDispatchNode;
import som.interpreter.nodes.enforced.EnforcedMessageSendNode;
import som.interpreter.nodes.enforced.EnforcedMessageSendNode.UninitializedEnforcedMessageSendNode;
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.nary.EagerBinaryPrimitiveNode;
import som.interpreter.nodes.nary.EagerUnaryPrimitiveNode;
import som.interpreter.nodes.specialized.AndMessageNodeFactory;
import som.interpreter.nodes.specialized.AndMessageNodeFactory.AndBoolMessageNodeFactory;
import som.interpreter.nodes.specialized.IfFalseMessageNodeFactory;
import som.interpreter.nodes.specialized.IfTrueIfFalseMessageNodeFactory;
import som.interpreter.nodes.specialized.IfTrueMessageNodeFactory;
import som.interpreter.nodes.specialized.IntToByDoMessageNodeFactory;
import som.interpreter.nodes.specialized.IntToDoMessageNodeFactory;
import som.interpreter.nodes.specialized.NotMessageNodeFactory;
import som.interpreter.nodes.specialized.OrMessageNodeFactory;
import som.interpreter.nodes.specialized.OrMessageNodeFactory.OrBoolMessageNodeFactory;
import som.interpreter.nodes.specialized.whileloops.WhileWithDynamicBlocksNode;
import som.interpreter.nodes.specialized.whileloops.WhileWithStaticBlocksNode.WhileFalseStaticBlocksNode;
import som.interpreter.nodes.specialized.whileloops.WhileWithStaticBlocksNode.WhileTrueStaticBlocksNode;
import som.primitives.ArrayPrimsFactory.AtPrimFactory;
import som.primitives.ArrayPrimsFactory.NewPrimFactory;
import som.primitives.BlockPrimsFactory.ValueNonePrimFactory;
import som.primitives.BlockPrimsFactory.ValueOnePrimFactory;
import som.primitives.EqualsEqualsPrimFactory;
import som.primitives.EqualsPrimFactory;
import som.primitives.IntegerPrimsFactory.LeftShiftPrimFactory;
import som.primitives.LengthPrimFactory;
import som.primitives.MethodPrimsFactory.InvokeOnPrimFactory;
import som.primitives.ObjectPrimsFactory.InstVarAtPrimFactory;
import som.primitives.ObjectPrimsFactory.InstVarAtPutPrimFactory;
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
import som.primitives.reflection.PerformWithArgumentsInSuperclassPrimFactory.PerformEnforcedWithArgumentsInSuperclassPrimFactory;
import som.vm.NotYetImplementedException;
import som.vm.constants.Classes;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.source.SourceSection;


public final class MessageSendNode {

  public static AbstractMessageSendNode create(final SSymbol selector,
      final ExpressionNode[] arguments, final SourceSection source, final boolean executesEnforced) {
    if (executesEnforced) {
      return new UninitializedEnforcedMessageSendNode(selector, arguments, source);
    } else {
      return new UninitializedMessageSendNode(selector, arguments, source, executesEnforced);
    }
  }

  public static AbstractMessageSendNode createForPerformNodes(
      final SSymbol selector, final boolean executesEnforced) {
    return new UninitializedSymbolSendNode(selector, null, executesEnforced);
  }

  public static AbstractMessageSendNode createForPerformInSuperclassNodes(
      final SSymbol selector, final SClass lookupClass, final boolean executesEnforced) {
    return new GenericMessageSendNode(selector, null,
        SuperDispatchNode.create(selector, lookupClass, executesEnforced),
            null, executesEnforced);
  }

  public static GenericMessageSendNode createForStandardDomainHandler(
      final SSymbol selector, final SourceSection source,
      final boolean executesEnforced, final SClass lookupClass) {
    if (lookupClass == null) {
      return new GenericMessageSendNode(selector, null,
          new UninitializedDispatchNode(selector), source, executesEnforced);
    } else {
      return new GenericMessageSendNode(selector, null,
          SuperDispatchNode.create(selector, lookupClass, executesEnforced),
          source, executesEnforced);
    }
  }

  public static AbstractMessageSendNode createGeneric(final SSymbol selector,
      final ExpressionNode[] argumentNodes, final SourceSection source,
      final boolean executesEnforced) {
    if (executesEnforced) {
      return new EnforcedMessageSendNode(selector, argumentNodes, source);
    } else {
      return new GenericMessageSendNode(selector, argumentNodes,
        new UninitializedDispatchNode(selector), source, executesEnforced);
    }
  }

  public abstract static class AbstractMessageSendNode extends ExpressionNode
      implements PreevaluatedExpression {

    @Children protected final ExpressionNode[] argumentNodes;

    protected AbstractMessageSendNode(final ExpressionNode[] arguments,
        final SourceSection source, final boolean executesEnforced) {
      super(source, executesEnforced);
      this.argumentNodes = arguments;
    }

    public boolean isSuperSend() {
      return argumentNodes[0] instanceof ISuperReadNode;
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

  public abstract static class AbstractUninitializedMessageSendNode extends AbstractMessageSendNode {
    protected final SSymbol selector;

    protected AbstractUninitializedMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments, final SourceSection source,
        final boolean executesEnforced) {
      super(arguments, source, executesEnforced);
      this.selector = selector;
    }

    @Override
    public final Object doPreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      return specialize(arguments).
          doPreEvaluated(frame, arguments);
    }

    protected PreevaluatedExpression specialize(final Object[] arguments) {
      TruffleCompiler.transferToInterpreterAndInvalidate("Specialize Symbol Send Node");

      // first option is a super send, super sends are treated specially because
      // the receiver class is lexically determined
      if (isSuperSend()) {
        return makeSuperSend();
      }

      // We treat super sends separately for simplicity, might not be the
      // optimal solution, especially in cases were the knowledge of the
      // receiver class also allows us to do more specific things, but for the
      // moment  we will leave it at this.
      // TODO: revisit, and also do more specific optimizations for super sends.


      // let's organize the specializations by number of arguments
      // perhaps not the best, but one simple way to just get some order into
      // the chaos.

      PreevaluatedExpression result = this;

      switch (arguments.length) {
        case 1: {
           result = specializeUnary(arguments);
           if (result == this) {
             result = specializeUnaryOnValues(arguments);
           }
           break;
        }
        case 2: {
          result = specializeBinary(arguments);
          if (result == this) {
            result = specializeBinaryOnValues(arguments);
          }
          break;
        }
        case 3: {
          result = specializeTernary(arguments);
          if (result == this) {
            result = specializeTernaryOnValues(arguments);
          }
          break;
        }
        case 4: {
          result = specializeQuaternary(arguments);
          if (result == this) {
            result = specializeQuaternaryOnValues(arguments);
          }
          break;
        }
      }

      if (result == this) {
        return makeGenericSend();
      } else {
        return result;
      }
    }

    protected abstract PreevaluatedExpression makeSuperSend();

    protected PreevaluatedExpression specializeUnary(final Object[] args) {
      Object receiver = args[0];
      switch (selector.getString()) {
        // eagerly but cautious:
        case "length":
          if (receiver instanceof Object[]) {
            return replace(new EagerUnaryPrimitiveNode(selector,
                argumentNodes[0], LengthPrimFactory.create(executesEnforced, null), executesEnforced));
          }
          break;
      }
      return this;
    }

    private PreevaluatedExpression specializeUnaryOnValues(final Object[] args) {
      Object receiver = args[0];
      switch (selector.getString()) {
        // eagerly but cautious:
        case "value":
          if (receiver instanceof SBlock) {
            return replace(new EagerUnaryPrimitiveNode(selector,
                argumentNodes[0], ValueNonePrimFactory.create(executesEnforced, null), executesEnforced));
          }
          break;

        case "not":
          if (receiver instanceof Boolean) {
            return replace(new EagerUnaryPrimitiveNode(selector,
                argumentNodes[0], NotMessageNodeFactory.create(
                    getSourceSection(), executesEnforced, null),
                    executesEnforced));
          }
          break;
      }
      return this;
    }

    protected PreevaluatedExpression specializeBinary(final Object[] arguments) {
      switch (selector.getString()) {
        // TODO: find a better way for primitives, use annotation or something
        // eagerly but cautious:
        case "at:":
          if (arguments[0] instanceof Object[]) {
            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1],
                AtPrimFactory.create(executesEnforced, null, null), executesEnforced));
          }
        case "new:":
          if (arguments[0] == Classes.arrayClass) {
            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1],
                NewPrimFactory.create(executesEnforced, null, null), executesEnforced));
          }
          break;
        case "instVarAt:":
          if (!executesEnforced) {
            return replace(new EagerBinaryPrimitiveNode(selector,
                argumentNodes[0], argumentNodes[1],
                InstVarAtPrimFactory.create(executesEnforced, null, null),
                executesEnforced));
          }
          break;
      }

      return this;
    }

    protected PreevaluatedExpression specializeBinaryOnValues(final Object[] arguments) {
      switch (selector.getString()) {
        case "value:":
          if (arguments[0] instanceof SBlock) {
            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1],
                ValueOnePrimFactory.create(executesEnforced, null, null), executesEnforced));
          }
          break;

        case "ifTrue:":
          return replace(IfTrueMessageNodeFactory.create(arguments[0],
              arguments[1], getSourceSection(), executesEnforced,
              argumentNodes[0], argumentNodes[1]));
        case "ifFalse:":
          return replace(IfFalseMessageNodeFactory.create(arguments[0],
              arguments[1], getSourceSection(), executesEnforced,
              argumentNodes[0], argumentNodes[1]));

        case "<":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              LessThanPrimFactory.create(executesEnforced, null, null), executesEnforced));
        case "<=":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              LessThanOrEqualPrimFactory.create(executesEnforced, null, null), executesEnforced));
        case ">":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              GreaterThanPrimFactory.create(executesEnforced, null, null), executesEnforced));
        case "+":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              AdditionPrimFactory.create(executesEnforced, null, null), executesEnforced));
        case "-":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              SubtractionPrimFactory.create(executesEnforced, null, null), executesEnforced));
        case "*":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              MultiplicationPrimFactory.create(executesEnforced, null, null), executesEnforced));
        case "=":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              EqualsPrimFactory.create(executesEnforced, null, null), executesEnforced));
        case "==":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              EqualsEqualsPrimFactory.create(executesEnforced, null, null), executesEnforced));
        case "bitXor:":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              BitXorPrimFactory.create(executesEnforced, null, null), executesEnforced));
        case "//":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              DoubleDivPrimFactory.create(executesEnforced, null, null), executesEnforced));
        case "%":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              ModuloPrimFactory.create(executesEnforced, null, null), executesEnforced));
        case "/":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              DividePrimFactory.create(executesEnforced, null, null), executesEnforced));
        case "&":
          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
              argumentNodes[1],
              LogicAndPrimFactory.create(executesEnforced, null, null), executesEnforced));
        case "<<":
          if (arguments[0] instanceof Long) {
            return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
                argumentNodes[1],
                LeftShiftPrimFactory.create(executesEnforced, null, null), executesEnforced));
          }
          break;

      }
      return this;
    }

    protected PreevaluatedExpression specializeTernary(final Object[] arguments) {
      return this;
    }

    protected PreevaluatedExpression specializeTernaryOnValues(final Object[] arguments) {
      switch (selector.getString()) {
        case "ifTrue:ifFalse:":
          return replace(IfTrueIfFalseMessageNodeFactory.create(arguments[0],
              arguments[1], arguments[2], executesEnforced,
              argumentNodes[0], argumentNodes[1], argumentNodes[2]));
        case "to:do:":
          if (TypesGen.TYPES.isLong(arguments[0]) &&
              (TypesGen.TYPES.isLong(arguments[1]) ||
                  TypesGen.TYPES.isDouble(arguments[1])) &&
              TypesGen.TYPES.isSBlock(arguments[2])) {
            return replace(IntToDoMessageNodeFactory.create(this,
                (SBlock) arguments[2], executesEnforced, argumentNodes[0],
                argumentNodes[1], argumentNodes[2]));
          }
          break;
        case "invokeOn:with:":
          if (!executesEnforced) {
            return replace(InvokeOnPrimFactory.create(executesEnforced,
                argumentNodes[0], argumentNodes[1], argumentNodes[2]));
          }
          break;
        case "instVarAt:put:":
          if (!executesEnforced) {
            return replace(InstVarAtPutPrimFactory.create(executesEnforced,
              argumentNodes[0], argumentNodes[1], argumentNodes[2]));
          }
          break;
      }
      return this;
    }

    protected PreevaluatedExpression specializeQuaternary(
        final Object[] arguments) {
      switch (selector.getString()) {
        case "performEnforced:withArguments:inSuperclass:":
          if (!executesEnforced) {
            return replace(PerformEnforcedWithArgumentsInSuperclassPrimFactory.
              create(executesEnforced, argumentNodes[0],
                  argumentNodes[1], argumentNodes[2], argumentNodes[3]));
          }
          break;
      }
      return this;
    }

    protected PreevaluatedExpression specializeQuaternaryOnValues(
        final Object[] arguments) {
      switch (selector.getString()) {
        case "to:by:do:":
          return replace(IntToByDoMessageNodeFactory.create(this,
              (SBlock) arguments[3], executesEnforced, argumentNodes[0],
              argumentNodes[1], argumentNodes[2], argumentNodes[3]));
      }
      return this;
    }

    protected AbstractMessageSendNode makeGenericSend() {
      GenericMessageSendNode send = new GenericMessageSendNode(selector,
          argumentNodes,
          new UninitializedDispatchNode(selector),
          getSourceSection(), executesEnforced);
      return replace(send);
    }
  }

  private static final class UninitializedMessageSendNode
      extends AbstractUninitializedMessageSendNode {

    protected UninitializedMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments, final SourceSection source,
        final boolean executesEnforced) {
      super(selector, arguments, source, executesEnforced);
    }

    @Override
    protected PreevaluatedExpression makeSuperSend() {
      GenericMessageSendNode node = new GenericMessageSendNode(selector,
          argumentNodes, SuperDispatchNode.create(selector,
              (ISuperReadNode) argumentNodes[0], executesEnforced),
              getSourceSection(), executesEnforced);
      return replace(node);
    }

    @Override
    protected PreevaluatedExpression specializeBinary(final Object[] arguments) {
      switch (selector.getString()) {
        case "whileTrue:": {
          if (argumentNodes[1] instanceof BlockNode &&
              argumentNodes[0] instanceof BlockNode) {
            BlockNode argBlockNode = (BlockNode) argumentNodes[1];
            SBlock    argBlock     = (SBlock)    arguments[1];
            return replace(new WhileTrueStaticBlocksNode(
                (BlockNode) argumentNodes[0], argBlockNode,
                (SBlock) arguments[0],
                argBlock, getSourceSection(), executesEnforced));
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
                (SBlock) arguments[0], argBlock, getSourceSection(), executesEnforced));
          }
          break; // use normal send
        case "and:":
        case "&&":
          if (arguments[0] instanceof Boolean) {
            if (argumentNodes[1] instanceof BlockNode) {
              return replace(AndMessageNodeFactory.create((SBlock) arguments[1],
                  getSourceSection(), executesEnforced,
                  argumentNodes[0], argumentNodes[1]));
            } else if (arguments[1] instanceof Boolean) {
              return replace(AndBoolMessageNodeFactory.create(getSourceSection(),
                  executesEnforced,
                  argumentNodes[0], argumentNodes[1]));
            }
          }
          break;
        case "or:":
        case "||":
          if (arguments[0] instanceof Boolean) {
            if (argumentNodes[1] instanceof BlockNode) {
              return replace(OrMessageNodeFactory.create((SBlock) arguments[1],
                  getSourceSection(), executesEnforced,
                  argumentNodes[0], argumentNodes[1]));
            } else if (arguments[1] instanceof Boolean) {
              return replace(OrBoolMessageNodeFactory.create(
                  getSourceSection(), executesEnforced,
                  argumentNodes[0], argumentNodes[1]));
            }
          }
          break;
      }

      return super.specializeBinary(arguments);
    }
  }

  private static final class UninitializedSymbolSendNode
    extends AbstractUninitializedMessageSendNode {

    protected UninitializedSymbolSendNode(final SSymbol selector,
        final SourceSection source, final boolean executesEnforced) {
      super(selector, new ExpressionNode[0], source, executesEnforced);
    }

    @Override
    public boolean isSuperSend() {
      // TODO: is is correct?
      return false;
    }

    @Override
    protected PreevaluatedExpression makeSuperSend() {
      // should never be reached with isSuperSend() returning always false
      throw new NotYetImplementedException();
    }

    @Override
    protected PreevaluatedExpression specializeBinary(final Object[] arguments) {
      switch (selector.getString()) {
        case "whileTrue:": {
          if (arguments[1] instanceof SBlock && arguments[0] instanceof SBlock) {
            SBlock argBlock = (SBlock) arguments[1];
            return replace(new WhileWithDynamicBlocksNode((SBlock) arguments[0],
                argBlock, true, getSourceSection(), executesEnforced));
          }
          break;
        }
        case "whileFalse:":
          if (arguments[1] instanceof SBlock && arguments[0] instanceof SBlock) {
            SBlock    argBlock     = (SBlock)    arguments[1];
            return replace(new WhileWithDynamicBlocksNode(
                (SBlock) arguments[0], argBlock, false, getSourceSection(),
                executesEnforced));
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

    public GenericMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments,
        final AbstractDispatchNode dispatchNode, final SourceSection source,
        final boolean executesEnforced) {
      super(arguments, source, executesEnforced);
      this.selector = selector;
      this.dispatchNode = dispatchNode;
    }

    @Override
    public Object doPreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      SObject domain = SArguments.domain(frame);
      return dispatchNode.executeDispatch(frame, domain, executesEnforced, arguments);
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
