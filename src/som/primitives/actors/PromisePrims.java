package som.primitives.actors;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.AccessModifier;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.PromiseCallbackMessage;
import som.interpreter.actors.ReceivedMessage.ReceivedCallback;
import som.interpreter.actors.RegisterOnPromiseNode.RegisterWhenResolved;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;
import som.vm.Primitives.Specializer;
import som.vm.Symbols;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SSymbol;


public final class PromisePrims {

  public static class IsActorModule extends Specializer<ExpressionNode> {
    public IsActorModule(final Primitive prim, final NodeFactory<ExpressionNode> fact) { super(prim, fact); }

    @Override
    public boolean matches(final Object[] args, final ExpressionNode[] argNodes) {
      return args[0] == ActorClasses.ActorModule;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "actorsCreatePromisePair:", selector = "createPromisePair",
             specializer = IsActorModule.class, noWrapper = true)
  public abstract static class CreatePromisePairPrim extends UnaryExpressionNode {

    protected static final DirectCallNode create() {
      Dispatchable disp = SPromise.pairClass.getSOMClass().lookupMessage(
          withAndFactory, AccessModifier.PUBLIC);
      return Truffle.getRuntime().createDirectCallNode(((SInvokable) disp).getCallTarget());
    }

    public CreatePromisePairPrim(final boolean eagerWrapper, final SourceSection source) { super(eagerWrapper, source); }

    @Specialization
    public final SImmutableObject createPromisePair(final VirtualFrame frame,
        final Object nil, @Cached("create()") final DirectCallNode factory) {
      SPromise promise   = SPromise.createPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
      SResolver resolver = SPromise.createResolver(promise, "ctorPPair");
      return (SImmutableObject) factory.call(frame, new Object[] {SPromise.pairClass, promise, resolver});
    }

    private static final SSymbol withAndFactory = Symbols.symbolFor("with:and:");
  }

  // TODO: can we find another solution for megamorphics callers that
  //       does not require node creation? Might need a generic received node.
  @TruffleBoundary
  public static RootCallTarget createReceived(final SBlock callback) {
    RootCallTarget target = callback.getMethod().getCallTarget();
    ReceivedCallback node = new ReceivedCallback(target);
    return Truffle.getRuntime().createCallTarget(node);
  }

  @GenerateNodeFactory
  @ImportStatic(PromisePrims.class)
  @Primitive(primitive = "actorsWhen:resolved:", selector = "whenResolved:",
             receiverType = SPromise.class)
  public abstract static class WhenResolvedPrim extends BinaryComplexOperation {
    @Child protected RegisterWhenResolved registerNode = new RegisterWhenResolved();

    protected WhenResolvedPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization(guards = "blockMethod == callback.getMethod()", limit = "10")
    public final SPromise whenResolved(final SPromise promise,
        final SBlock callback,
        @Cached("callback.getMethod()") final SInvokable blockMethod,
        @Cached("createReceived(callback)") final RootCallTarget blockCallTarget) {
      return whenResolved(promise, callback, blockCallTarget, registerNode);
    }

    @Specialization(contains = "whenResolved")
    public final SPromise whenResolvedUncached(final SPromise promise, final SBlock callback) {
      return whenResolved(promise, callback, createReceived(callback), registerNode);
    }

    protected static final SPromise whenResolved(final SPromise rcvr,
        final SBlock block, final RootCallTarget blockCallTarget,
        final RegisterWhenResolved registerNode) {
      assert block.getMethod().getNumberOfArguments() == 2;

      Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();
      SPromise  promise  = SPromise.createPromise(current);
      SResolver resolver = SPromise.createResolver(promise, "wR:block");

      PromiseCallbackMessage msg = new PromiseCallbackMessage(EventualMessage.getCurrentExecutingMessageId(), rcvr.getOwner(),
          block, resolver, blockCallTarget, false, false, false, promise);
      registerNode.register(rcvr, msg, current);

      return promise;
    }
  }

  // TODO: should we add this for the literal case? which should be very common?
  public abstract static class WhenResolvedLiteralBlockNode extends BinaryComplexOperation {
    @SuppressWarnings("unused") private final RootCallTarget blockCallTarget;
    public WhenResolvedLiteralBlockNode(final SourceSection source, final BlockNode blockNode) {
      super(false, source);
      blockCallTarget = blockNode.getBlockMethod().getCallTarget();
    }
  }

  // TODO: should I add a literal version of OnErrorPrim??
  @GenerateNodeFactory
  @ImportStatic(PromisePrims.class)
  @Primitive(primitive = "actorsFor:onError:")
  public abstract static class OnErrorPrim extends BinaryComplexOperation {
    protected OnErrorPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization(guards = "blockMethod == callback.getMethod()")
    public final SPromise onError(final SPromise promise,
        final SBlock callback,
        @Cached("callback.getMethod()") final SInvokable blockMethod,
        @Cached("createReceived(callback)") final RootCallTarget blockCallTarget) {
      Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();
      return promise.onError(callback, blockCallTarget, current);
    }
  }

  @GenerateNodeFactory
  @ImportStatic(PromisePrims.class)
  @Primitive(primitive = "actorsFor:on:do:")
  public abstract static class OnExceptionDoPrim extends TernaryExpressionNode {
    public OnExceptionDoPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization(guards = "blockMethod == callback.getMethod()")
    public final SPromise onExceptionDo(final SPromise promise,
        final SClass exceptionClass, final SBlock callback,
        @Cached("callback.getMethod()") final SInvokable blockMethod,
        @Cached("createReceived(callback)") final RootCallTarget blockCallTarget) {
      Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();
      return promise.onException(exceptionClass, callback, blockCallTarget, current);
    }
  }

  @GenerateNodeFactory
  @ImportStatic(PromisePrims.class)
  @Primitive(primitive = "actorsWhen:resolved:onError:")
  public abstract static class WhenResolvedOnErrorPrim extends TernaryExpressionNode {
    @Child protected RegisterWhenResolved registerNode = new RegisterWhenResolved();

    public WhenResolvedOnErrorPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization(guards = {"resolvedMethod == resolved.getMethod()", "errorMethod == error.getMethod()"})
    public final SPromise whenResolvedOnError(final SPromise promise,
        final SBlock resolved, final SBlock error,
        @Cached("resolved.getMethod()") final SInvokable resolvedMethod,
        @Cached("createReceived(resolved)") final RootCallTarget resolvedTarget,
        @Cached("error.getMethod()") final SInvokable errorMethod,
        @Cached("createReceived(error)") final RootCallTarget errorTarget) {
      return whenResolvedOrError(promise, resolved, error, resolvedTarget,
          errorTarget, registerNode);
    }

    protected static final SPromise whenResolvedOrError(final SPromise rcvr,
        final SBlock resolved, final SBlock error,
        final RootCallTarget resolverTarget, final RootCallTarget errorTarget,
        final RegisterWhenResolved registerNode) {
      assert resolved.getMethod().getNumberOfArguments() == 2;
      assert error.getMethod().getNumberOfArguments() == 2;

      Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();
      SPromise  promise  = SPromise.createPromise(current);
      SResolver resolver = SPromise.createResolver(promise, "wROE:block:block");

      PromiseCallbackMessage onResolved = new PromiseCallbackMessage(EventualMessage.getCurrentExecutingMessageId(), rcvr.getOwner(), resolved, resolver, resolverTarget, false, false, false, rcvr);
      PromiseCallbackMessage onError    = new PromiseCallbackMessage(EventualMessage.getCurrentExecutingMessageId(), rcvr.getOwner(), error, resolver, errorTarget, false, false, false, rcvr);

      synchronized (rcvr) {
        registerNode.register(rcvr, onResolved, current);
        rcvr.registerOnError(onError, current);
      }
      return promise;
    }
  }
}
