package som.primitives.actors;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.AccessModifier;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.PromiseCallbackMessage;
import som.interpreter.actors.ReceivedMessage.ReceivedCallback;
import som.interpreter.actors.RegisterOnPromiseNode.RegisterOnError;
import som.interpreter.actors.RegisterOnPromiseNode.RegisterWhenResolved;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;
import som.vm.Primitives.Specializer;
import som.vm.Symbols;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SSymbol;


public final class PromisePrims {

  public static class IsActorModule extends Specializer<ExpressionNode> {
    public IsActorModule(final Primitive prim, final NodeFactory<ExpressionNode> fact) { super(prim, fact); }

    @Override
    public boolean matches(final Object[] args, final ExpressionNode[] argNodes) {
      // XXX: this is the case when doing parse-time specialization
      if (args == null) { return true; }

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
    public final SImmutableObject createPromisePair(final Object nil,
        @Cached("create()") final DirectCallNode factory) {
      SPromise promise   = SPromise.createPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
      SResolver resolver = SPromise.createResolver(promise, "ctorPPair");
      return (SImmutableObject) factory.call(new Object[] {SPromise.pairClass, promise, resolver});
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
  @Primitive(primitive = "actorsWhen:resolved:",
             receiverType = SPromise.class)
  public abstract static class WhenResolvedPrim extends BinaryComplexOperation {
    @Child protected RegisterWhenResolved registerNode = new RegisterWhenResolved();

    protected WhenResolvedPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization(guards = "blockMethod == callback.getMethod()", limit = "10")
    public final SPromise whenResolved(final SPromise promise,
        final SBlock callback,
        @Cached("callback.getMethod()") final SInvokable blockMethod,
        @Cached("createReceived(callback)") final RootCallTarget blockCallTarget) {
      return registerWhenResolved(promise, callback, blockCallTarget, registerNode);
    }

    @Specialization(replaces = "whenResolved")
    public final SPromise whenResolvedUncached(final SPromise promise, final SBlock callback) {
      return registerWhenResolved(promise, callback, createReceived(callback), registerNode);
    }

    protected static final SPromise registerWhenResolved(final SPromise rcvr,
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

  // TODO: should I add a literal version of OnErrorPrim??
  @GenerateNodeFactory
  @ImportStatic(PromisePrims.class)
  @Primitive(primitive = "actorsFor:onError:")
  public abstract static class OnErrorPrim extends BinaryComplexOperation {
    @Child protected RegisterOnError registerNode = new RegisterOnError();

    protected OnErrorPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization(guards = "blockMethod == callback.getMethod()", limit = "10")
    public final SPromise onError(final SPromise promise,
        final SBlock callback,
        @Cached("callback.getMethod()") final SInvokable blockMethod,
        @Cached("createReceived(callback)") final RootCallTarget blockCallTarget) {
      return registerOnError(promise, callback, blockCallTarget, registerNode);
    }

    @Specialization(replaces = "onError")
    public final SPromise whenResolvedUncached(final SPromise promise, final SBlock callback) {
      return registerOnError(promise, callback, createReceived(callback), registerNode);
    }

    protected static final SPromise registerOnError(final SPromise rcvr,
        final SBlock block, final RootCallTarget blockCallTarget,
        final RegisterOnError registerNode) {
      assert block.getMethod().getNumberOfArguments() == 2;

      Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();
      SPromise  promise  = SPromise.createPromise(current);
      SResolver resolver = SPromise.createResolver(promise, "onE:block");

      PromiseCallbackMessage msg = new PromiseCallbackMessage(EventualMessage.getCurrentExecutingMessageId(), rcvr.getOwner(),
          block, resolver, blockCallTarget, false, false, false, promise);
      registerNode.register(rcvr, msg, current);

      return promise;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(PromisePrims.class)
  @Primitive(primitive = "actorsWhen:resolved:onError:")
  public abstract static class WhenResolvedOnErrorPrim extends TernaryExpressionNode {
    @Child protected RegisterWhenResolved registerWhenResolved = new RegisterWhenResolved();
    @Child protected RegisterOnError registerOnError = new RegisterOnError();

    public WhenResolvedOnErrorPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization(guards = {"resolvedMethod == resolved.getMethod()", "errorMethod == error.getMethod()"})
    public final SPromise whenResolvedOnError(final SPromise promise,
        final SBlock resolved, final SBlock error,
        @Cached("resolved.getMethod()") final SInvokable resolvedMethod,
        @Cached("createReceived(resolved)") final RootCallTarget resolvedTarget,
        @Cached("error.getMethod()") final SInvokable errorMethod,
        @Cached("createReceived(error)") final RootCallTarget errorTarget) {
      return registerWhenResolvedOrError(promise, resolved, error, resolvedTarget,
          errorTarget, registerWhenResolved, registerOnError);
    }

    @Specialization(replaces = "whenResolvedOnError")
    public final SPromise whenResolvedOnErrorUncached(final SPromise promise,
        final SBlock resolved, final SBlock error) {
      return registerWhenResolvedOrError(promise, resolved, error, createReceived(resolved),
          createReceived(error), registerWhenResolved, registerOnError);
    }

    protected static final SPromise registerWhenResolvedOrError(final SPromise rcvr,
        final SBlock resolved, final SBlock error,
        final RootCallTarget resolverTarget, final RootCallTarget errorTarget,
        final RegisterWhenResolved registerWhenResolved,
        final RegisterOnError registerOnError) {
      assert resolved.getMethod().getNumberOfArguments() == 2;
      assert error.getMethod().getNumberOfArguments()    == 2;

      Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();
      SPromise  promise  = SPromise.createPromise(current);
      SResolver resolver = SPromise.createResolver(promise, "wROE:block:block");

      PromiseCallbackMessage onResolved = new PromiseCallbackMessage(EventualMessage.getCurrentExecutingMessageId(), rcvr.getOwner(), resolved, resolver, resolverTarget, false, false, false, rcvr);
      PromiseCallbackMessage onError    = new PromiseCallbackMessage(EventualMessage.getCurrentExecutingMessageId(), rcvr.getOwner(), error,    resolver, errorTarget,    false, false, false, rcvr);

      synchronized (rcvr) {
        registerWhenResolved.register(rcvr, onResolved, current);
        registerOnError.register(rcvr, onError, current);
      }
      return promise;
    }
  }
}
