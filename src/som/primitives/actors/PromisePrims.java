package som.primitives.actors;

import som.compiler.AccessModifier;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.PromiseCallbackMessage;
import som.interpreter.actors.ReceivedMessage.ReceivedCallback;
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
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;


public final class PromisePrims {

  @GenerateNodeFactory
  @Primitive("actorsCreatePromisePair:")
  public abstract static class CreatePromisePairPrim extends UnaryExpressionNode {

    protected final DirectCallNode create() {
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
    @Specialization(guards = "blockMethod == callback.getMethod()", limit = "6")
    public final SPromise whenResolved(final SPromise promise,
        final SBlock callback,
        @Cached("callback.getMethod()") final SInvokable blockMethod,
        @Cached("createReceived(callback)") final RootCallTarget blockCallTarget) {
      return whenResolved(promise, callback, blockCallTarget);
    }

    @Fallback
    public final SPromise whenResolved(final SPromise promise, final SBlock callback) {
      return whenResolved(promise, callback, createReceived(callback));
    }

    protected static final SPromise whenResolved(final SPromise rcvr,
        final SBlock block, final RootCallTarget blockCallTarget) {
      assert block.getMethod().getNumberOfArguments() == 2;

      SPromise  promise  = SPromise.createPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
      SResolver resolver = SPromise.createResolver(promise, "wR:block");

      PromiseCallbackMessage msg = new PromiseCallbackMessage(rcvr.getOwner(),
          block, resolver, blockCallTarget);
      rcvr.registerWhenResolved(msg);

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
      return promise.onError(callback, blockCallTarget);
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
      return promise.onException(exceptionClass, callback, blockCallTarget);
    }
  }

  @GenerateNodeFactory
  @ImportStatic(PromisePrims.class)
  @Primitive("actorsWhen:resolved:onError:")
  public abstract static class WhenResolvedOnErrorPrim extends TernaryExpressionNode {
    @Specialization(guards = {"resolvedMethod == resolved.getMethod()", "errorMethod == error.getMethod()"})
    public final SPromise whenResolvedOnError(final SPromise promise,
        final SBlock resolved, final SBlock error,
        @Cached("resolved.getMethod()") final SInvokable resolvedMethod,
        @Cached("createReceived(resolved)") final RootCallTarget resolvedTarget,
        @Cached("error.getMethod()") final SInvokable errorMethod,
        @Cached("createReceived(error)") final RootCallTarget errorTarget) {
      return promise.whenResolvedOrError(resolved, error, resolvedTarget, errorTarget);
    }
  }
}
