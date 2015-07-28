package som.primitives.actors;

import som.VM;
import som.compiler.AccessModifier;
import som.interpreter.actors.EventualMessage;
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;


public class PromisePrims {

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
      SPromise promise = new SPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
      SResolver resolver = new SResolver(promise);
      return (SImmutableObject) factory.call(frame, new Object[] {SPromise.pairClass, promise, resolver});
    }

    private static final SSymbol withAndFactory = Symbols.symbolFor("with:and:");
  }

  @GenerateNodeFactory
  @Primitive("actorsResolve:with:")
  public abstract static class ResolvePrim extends BinaryExpressionNode {
    @Specialization
    public final SResolver resolve(final SResolver resolver, final Object value) {
      if (CompilerDirectives.inInterpreter()) {
        // TODO: this can be done unconditionally in some uninitialized version
        //       of EventualSendNode, which we probably should have at some point
        VM.hasSendMessages();
      }

      resolver.resolve(value);
      return resolver;
    }
  }

  @GenerateNodeFactory
  @Primitive("actorsWhen:resolved:")
  public abstract static class WhenResolvedPrim extends BinaryExpressionNode {
    @Specialization
    public final SPromise whenResolved(final SPromise promise, final SBlock callback) {
      return promise.whenResolved(callback);
    }
  }

  @GenerateNodeFactory
  @Primitive("actorsFor:onError:")
  public abstract static class OnErrorPrim extends BinaryExpressionNode {
    @Specialization
    public final SPromise onError(final SPromise promise, final SBlock callback) {
      return promise.onError(callback);
    }
  }

  @GenerateNodeFactory
  @Primitive("actorsFor:on:do:")
  public abstract static class OnExceptionDoPrim extends TernaryExpressionNode {
    @Specialization
    public final SPromise onExceptionDo(final SPromise promise, final SClass exceptionClass, final SBlock callback) {
      return promise.onException(exceptionClass, callback);
    }
  }

  @GenerateNodeFactory
  @Primitive("actorsWhen:resolved:onError:")
  public abstract static class WhenResolvedOnErrorPrim extends TernaryExpressionNode {
    @Specialization
    public final SPromise whenResolvedOnError(final SPromise promise, final SBlock resolved, final SBlock error) {
      return promise.whenResolvedOrError(resolved, error);
    }
  }

}
