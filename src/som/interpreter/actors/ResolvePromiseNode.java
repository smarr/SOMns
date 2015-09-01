package som.interpreter.actors;

import som.VM;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.primitives.Primitive;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


@GenerateNodeFactory
@Primitive("actorsResolve:with:")
public abstract class ResolvePromiseNode extends BinaryExpressionNode {

  public abstract Object executeEvaluated(final SResolver receiver, Object argument);

  @Specialization
  public final SResolver resolution(final SResolver resolver, final Object result) {
    if (CompilerDirectives.inInterpreter()) {
      // we need to set this flag once, this should be reasonably OK
      VM.hasSendMessages();
    }

    if (result instanceof SPromise) {
      if (resolver.getPromise() == result) {
        return resolver;
      }

      assert resolver.assertNotResolved();
      SPromise promise = resolver.getPromise();
      synchronized (promise) { // TODO: is this really deadlock free?
        ((SPromise) result).addChainedPromise(promise);
      }
      return resolver;
    } else {
      SPromise promise = resolver.getPromise();

      Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();
      Object wrapped = promise.owner.wrapForUse(result, current);

      SResolver.resolveAndTriggerListeners(result, wrapped, promise, current);
      return resolver;
    }
  }
}
