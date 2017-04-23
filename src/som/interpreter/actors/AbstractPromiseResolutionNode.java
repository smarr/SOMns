package som.interpreter.actors;

import java.util.concurrent.ForkJoinPool;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.interpreter.actors.SPromise.Resolution;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;


@Instrumentable(factory = AbstractPromiseResolutionNodeWrapper.class)
public abstract class AbstractPromiseResolutionNode extends TernaryExpressionNode {
  private final ForkJoinPool actorPool;

  @Child protected WrapReferenceNode wrapper = WrapReferenceNodeGen.create();
  @Child protected UnaryExpressionNode haltNode;

  protected AbstractPromiseResolutionNode(final boolean eagWrap, final SourceSection source, final ForkJoinPool actorPool) {
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
      SResolver receiver, Object argument, boolean isBreakpointOnPromiseResolution);

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
    boolean breakpointOnResolution = isBreakpointOnPromiseResolution;
    synchronized (promiseValue) {
      Resolution state = promiseValue.getResolutionStateUnsync();
      if (SPromise.isCompleted(state)) {
        resolvePromise(state, resolver, promiseValue.getValueUnsync(),
            breakpointOnResolution);
      } else {
        synchronized (promiseToBeResolved) { // TODO: is this really deadlock free?
          if (promiseValue.isTriggerPromiseResolutionBreakpoint() && !breakpointOnResolution) {
            breakpointOnResolution = promiseValue.isTriggerPromiseResolutionBreakpoint();
          }
          promiseToBeResolved.setTriggerResolutionBreakpointOnUnresolvedChainedPromise(breakpointOnResolution);
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

    resolve(type, wrapper, promise, result, current, actorPool,
        isBreakpointOnPromiseResolution);
  }

  public static void resolve(final Resolution type,
      final WrapReferenceNode wrapper,
      final SPromise promise, final Object result, final Actor current,
      final ForkJoinPool actorPool,
      final boolean isBreakpointOnPromiseResolution) {
    Object wrapped = wrapper.execute(result, promise.owner, current);
    SResolver.resolveAndTriggerListenersUnsynced(type, result, wrapped, promise,
        current, actorPool, isBreakpointOnPromiseResolution);
  }
}
