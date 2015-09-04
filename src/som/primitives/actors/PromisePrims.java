package som.primitives.actors;

import som.compiler.AccessModifier;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.PromiseCallbackMessage;
import som.interpreter.actors.ReceivedMessage.ReceivedCallback;
import som.interpreter.actors.RegisterOnPromiseNode.RegisterWhenResolved;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;
import som.vm.Symbols;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;


public final class PromisePrims {

  @GenerateNodeFactory
  @Primitive("actorsCreatePromisePair:")
  public abstract static class CreatePromisePairPrim extends UnaryExpressionNode {

    protected static final DirectCallNode create() {
      Dispatchable disp = SPromise.pairClass.getSOMClass().lookupMessage(withAndFactory, AccessModifier.PUBLIC);
      return Truffle.getRuntime().createDirectCallNode(disp.getCallTarget());
    }

    @Specialization
    public final SImmutableObject createPromisePair(final VirtualFrame frame,
        final Object nil, @Cached("create()") final DirectCallNode factory) {
      SPromise promise   = SPromise.createPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
      SResolver resolver = SPromise.createResolver(promise, "ctorPPair");
      return (SImmutableObject) factory.call(frame, new Object[] {SPromise.pairClass, promise, resolver});
    }

    private static final SSymbol withAndFactory = Symbols.symbolFor("with:and:");
  }

  public static RootCallTarget createReceived(final SBlock callback) {
    RootCallTarget target = callback.getMethod().getCallTarget();
    ReceivedCallback node = new ReceivedCallback(target);
    return Truffle.getRuntime().createCallTarget(node);
  }

  @GenerateNodeFactory
  @ImportStatic(PromisePrims.class)
  @Primitive("actorsWhen:resolved:")
  public abstract static class WhenResolvedPrim extends BinaryExpressionNode {
    @Child protected RegisterWhenResolved registerNode = new RegisterWhenResolved();

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

      PromiseCallbackMessage msg = new PromiseCallbackMessage(rcvr.getOwner(),
          block, resolver, blockCallTarget);
      registerNode.register(rcvr, msg, current);

      return promise;
    }
  }

  // TODO: should we add this for the literal case? which should be very common?
  public abstract static class WhenResolvedLiteralBlockNode extends BinaryExpressionNode {
    private final RootCallTarget blockCallTarget;
    public WhenResolvedLiteralBlockNode(final BlockNode blockNode) {
      blockCallTarget = blockNode.getBlockMethod().getCallTarget();
    }
  }

  // TODO: should I add a literal version of OnErrorPrim??
  @GenerateNodeFactory
  @ImportStatic(PromisePrims.class)
  @Primitive("actorsFor:onError:")
  public abstract static class OnErrorPrim extends BinaryExpressionNode {
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
  @Primitive("actorsFor:on:do:")
  public abstract static class OnExceptionDoPrim extends TernaryExpressionNode {
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
  @Primitive("actorsWhen:resolved:onError:")
  public abstract static class WhenResolvedOnErrorPrim extends TernaryExpressionNode {
    @Child protected RegisterWhenResolved registerNode = new RegisterWhenResolved();

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

      PromiseCallbackMessage onResolved = new PromiseCallbackMessage(rcvr.getOwner(), resolved, resolver, resolverTarget);
      PromiseCallbackMessage onError    = new PromiseCallbackMessage(rcvr.getOwner(), error, resolver, errorTarget);

      synchronized (rcvr) {
        registerNode.register(rcvr, onResolved, current);
        rcvr.registerOnError(onError, current);
      }
      return promise;
    }
  }
}
