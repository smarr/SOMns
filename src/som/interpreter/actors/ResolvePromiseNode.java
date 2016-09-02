package som.interpreter.actors;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.primitives.Primitive;


@GenerateNodeFactory
@Primitive(primitive = "actorsResolve:with:")
@Instrumentable(factory = ResolvePromiseNodeWrapper.class)
public abstract class ResolvePromiseNode extends BinaryComplexOperation {
  protected ResolvePromiseNode(final SourceSection source) { super(false, source); }

  public abstract Object executeEvaluated(VirtualFrame frame, final SResolver receiver, Object argument);

  @Specialization(guards = {"resolver.getPromise() == result"})
  public SResolver selfResolution(final SResolver resolver, final SPromise result) {
    return resolver;
  }

  @Specialization(guards = {"resolver.getPromise() != result"})
  public SResolver chainedPromise(final SResolver resolver, final SPromise result) {
    assert resolver.assertNotCompleted();
    SPromise promise = resolver.getPromise();
    synchronized (promise) { // TODO: is this really deadlock free?
      result.addChainedPromise(promise);
    }
    return resolver;
  }

  protected static boolean notAPromise(final Object result) {
    return !(result instanceof SPromise);
  }

  @Child protected WrapReferenceNode wrapper = WrapReferenceNodeGen.create();

  @Specialization(guards = {"notAPromise(result)"})
  public SResolver normalResolution(final SResolver resolver, final Object result) {
    SPromise promise = resolver.getPromise();

    Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();
    Object wrapped = wrapper.execute(result, promise.owner, current);

    SResolver.resolveAndTriggerListenersUnsynced(result, wrapped, promise, current);
    return resolver;
  }
}
