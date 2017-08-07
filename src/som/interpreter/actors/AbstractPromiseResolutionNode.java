package som.interpreter.actors;

import java.util.concurrent.ForkJoinPool;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.interpreter.actors.SPromise.Resolution;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.nary.QuaternaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.VmSettings;
import tools.concurrency.ActorExecutionTrace;


@Instrumentable(factory = AbstractPromiseResolutionNodeWrapper.class)
public abstract class AbstractPromiseResolutionNode extends QuaternaryExpressionNode {
  private final ForkJoinPool actorPool;

  @Child protected WrapReferenceNode   wrapper = WrapReferenceNodeGen.create();
  @Child protected UnaryExpressionNode haltNode;

  protected AbstractPromiseResolutionNode(final boolean eagWrap, final SourceSection source,
      final ForkJoinPool actorPool) {
    super(eagWrap, source);
    this.actorPool = actorPool;
    haltNode = insert(SuspendExecutionNodeGen.create(false, source, 2, null));
    VM.insertInstrumentationWrapper(haltNode);
  }

  protected AbstractPromiseResolutionNode(final AbstractPromiseResolutionNode node) {
    super(node);
    this.actorPool = node.actorPool;
    haltNode = insert(SuspendExecutionNodeGen.create(false, node.getSourceSection(), 2, null));
    VM.insertInstrumentationWrapper(haltNode);
  }

  public abstract Object executeEvaluated(VirtualFrame frame,
      SResolver receiver, Object argument, boolean haltOnResolver,
      boolean haltOnResolution);

  /**
   * To avoid cycles in the promise chain, do nothing when a promise is resolved with itself.
   */
  @Specialization(guards = {"resolver.getPromise() == result"})
  public SResolver selfResolution(final SResolver resolver,
      final SPromise result, final boolean haltOnResolver,
      final boolean haltOnResolution) {
    return resolver;
  }

  /**
   * Handle the case that a promise is resolved with another promise, which is not itself.
   */
  @Specialization(guards = {"resolver.getPromise() != promiseValue"})
  public SResolver chainedPromise(final VirtualFrame frame,
      final SResolver resolver, final SPromise promiseValue,
      final boolean haltOnResolver, final boolean haltOnResolution) {
    chainPromise(resolver, promiseValue, haltOnResolver, haltOnResolution);
    return resolver;
  }

  protected static boolean notAPromise(final Object result) {
    return !(result instanceof SPromise);
  }

  protected void chainPromise(final SResolver resolver,
      final SPromise promiseValue, final boolean haltOnResolver,
      final boolean haltOnResolution) {
    assert resolver.assertNotCompleted();
    SPromise promiseToBeResolved = resolver.getPromise();
    if (VmSettings.PROMISE_RESOLUTION) {
      ActorExecutionTrace.promiseChained(
          promiseValue.getPromiseId(), promiseToBeResolved.getPromiseId());
    }

    synchronized (promiseValue) {
      Resolution state = promiseValue.getResolutionStateUnsync();
      if (SPromise.isCompleted(state)) {
        resolvePromise(state, resolver, promiseValue.getValueUnsync(),
            haltOnResolution);
      } else {
        synchronized (promiseToBeResolved) { // TODO: is this really deadlock free?
          if (haltOnResolution || promiseValue.getHaltOnResolution()) {
            promiseToBeResolved.enableHaltOnResolution();
          }
          promiseValue.addChainedPromise(promiseToBeResolved);
        }
      }
    }
  }

  protected void resolvePromise(final Resolution type,
      final SResolver resolver, final Object result,
      final boolean haltOnResolution) {
    SPromise promise = resolver.getPromise();
    Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();

    resolve(type, wrapper, promise, result, current, actorPool, haltOnResolution);
  }

  public static void resolve(final Resolution type,
      final WrapReferenceNode wrapper, final SPromise promise,
      final Object result, final Actor current, final ForkJoinPool actorPool,
      final boolean haltOnResolution) {
    Object wrapped = wrapper.execute(result, promise.owner, current);
    SResolver.resolveAndTriggerListenersUnsynced(type, result, wrapped, promise,
        current, actorPool, haltOnResolution);
  }
}
