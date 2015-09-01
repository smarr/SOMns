package som.primitives.actors;

import som.compiler.AccessModifier;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.PromiseCallbackMessage;
import som.interpreter.actors.RegisterOnPromiseNode.RegisterWhenResolved;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;
import som.vm.Symbols;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SSymbol;

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

  @GenerateNodeFactory
  @ImportStatic(PromisePrims.class)
  @Primitive("actorsWhen:resolved:")
  public abstract static class WhenResolvedPrim extends BinaryExpressionNode {
    @Child protected RegisterWhenResolved registerNode = new RegisterWhenResolved();

    @Specialization
    public final SPromise whenResolved(final SPromise promise, final SBlock callback) {
      return whenResolved(promise, callback, registerNode);
    }

    protected static final SPromise whenResolved(final SPromise rcvr,
        final SBlock block,
        final RegisterWhenResolved registerNode) {
      assert block.getMethod().getNumberOfArguments() == 2;

      Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();
      SPromise  promise  = SPromise.createPromise(current);
      SResolver resolver = SPromise.createResolver(promise, "wR:block");

      PromiseCallbackMessage msg = new PromiseCallbackMessage(rcvr.getOwner(),
          block, resolver);
      registerNode.register(rcvr, msg, current);

      return promise;
    }
  }

  // TODO: should I add a literal version of OnErrorPrim??
  @GenerateNodeFactory
  @ImportStatic(PromisePrims.class)
  @Primitive("actorsFor:onError:")
  public abstract static class OnErrorPrim extends BinaryExpressionNode {
    @Specialization
    public final SPromise onError(final SPromise promise,
        final SBlock callback) {
      Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();
      return promise.onError(callback, current);
    }
  }

  @GenerateNodeFactory
  @ImportStatic(PromisePrims.class)
  @Primitive("actorsFor:on:do:")
  public abstract static class OnExceptionDoPrim extends TernaryExpressionNode {
    @Specialization
    public final SPromise onExceptionDo(final SPromise promise,
        final SClass exceptionClass, final SBlock callback) {
      Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();
      return promise.onException(exceptionClass, callback, current);
    }
  }

  @GenerateNodeFactory
  @ImportStatic(PromisePrims.class)
  @Primitive("actorsWhen:resolved:onError:")
  public abstract static class WhenResolvedOnErrorPrim extends TernaryExpressionNode {
    @Child protected RegisterWhenResolved registerNode = new RegisterWhenResolved();

    @Specialization
    public final SPromise whenResolvedOnError(final SPromise promise,
        final SBlock resolved, final SBlock error) {
      return whenResolvedOrError(promise, resolved, error, registerNode);
    }

    protected static final SPromise whenResolvedOrError(final SPromise rcvr,
        final SBlock resolved, final SBlock error,
        final RegisterWhenResolved registerNode) {
      assert resolved.getMethod().getNumberOfArguments() == 2;
      assert error.getMethod().getNumberOfArguments() == 2;

      Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();
      SPromise  promise  = SPromise.createPromise(current);
      SResolver resolver = SPromise.createResolver(promise, "wROE:block:block");

      PromiseCallbackMessage onResolved = new PromiseCallbackMessage(rcvr.getOwner(), resolved, resolver);
      PromiseCallbackMessage onError    = new PromiseCallbackMessage(rcvr.getOwner(), error, resolver);

      synchronized (rcvr) {
        registerNode.register(rcvr, onResolved, current);
        rcvr.registerOnError(onError, current);
      }
      return promise;
    }
  }
}
