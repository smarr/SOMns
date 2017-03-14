package som.interpreter.actors;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.SPromise.Resolution;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.nary.TernaryExpressionNode;


@Instrumentable(factory = AbstractPromiseResolutionNodeWrapper.class)
public abstract class AbstractPromiseResolutionNode extends TernaryExpressionNode {

  @Child protected WrapReferenceNode wrapper = WrapReferenceNodeGen.create();

  protected AbstractPromiseResolutionNode(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }
  protected AbstractPromiseResolutionNode(final AbstractPromiseResolutionNode node) { super(node); }

  public abstract Object executeEvaluated(final VirtualFrame frame,
      final SResolver receiver, Object argument,
      final boolean isBreakpointOnPromiseResolution);

  /**
   * To avoid cycles in the promise chain, do nothing when a promise is resolved with itself.
   */
  @Specialization(guards = {"resolver.getPromise() == result"})
  public SResolver selfResolution(final SResolver resolver, final SPromise result,
      final boolean isBreakpointOnPromiseResolution) {
    return resolver;
  }

  /**
   * Handle the case that a promise is resolved with another promise, which is not itself.
   */
  @Specialization(guards = {"resolver.getPromise() != promiseValue"})
  public SResolver chainedPromise(final VirtualFrame frame,
      final SResolver resolver, final SPromise promiseValue,
      final boolean isBreakpointOnPromiseResolution) {
    chainPromise(resolver, promiseValue, isBreakpointOnPromiseResolution);
    return resolver;
  }

  protected static boolean notAPromise(final Object result) {
    return !(result instanceof SPromise);
  }

  protected void chainPromise(final SResolver resolver,
      final SPromise promiseValue,
      final boolean isBreakpointOnPromiseResolution) {
    assert resolver.assertNotCompleted();
    SPromise promiseToBeResolved = resolver.getPromise();

    synchronized (promiseValue) {
      Resolution state = promiseValue.getResolutionStateUnsync();
      if (SPromise.isCompleted(state)) {
        resolvePromise(state, resolver, promiseValue.getValueUnsync(),
           isBreakpointOnPromiseResolution);
      } else {
        synchronized (promiseToBeResolved) { // TODO: is this really deadlock free?
          promiseToBeResolved.setTriggerResolutionBreakpointOnUnresolvedChainedPromise(isBreakpointOnPromiseResolution);
          promiseValue.addChainedPromise(promiseToBeResolved);
        }
      }
    }
  }

  protected void resolvePromise(final Resolution type,
      final SResolver resolver, final Object result,
      final boolean isBreakpointOnPromiseResolution) {
    SPromise promise = resolver.getPromise();
    Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();

    resolve(type, wrapper, promise, result, current,
        isBreakpointOnPromiseResolution);
  }

  public static void resolve(final Resolution type,
      final WrapReferenceNode wrapper,
      final SPromise promise, final Object result, final Actor current,
      final boolean isBreakpointOnPromiseResolution) {
    Object wrapped = wrapper.execute(result, promise.owner, current);
    SResolver.resolveAndTriggerListenersUnsynced(type, result, wrapped, promise,
        current, isBreakpointOnPromiseResolution);
  }
}
