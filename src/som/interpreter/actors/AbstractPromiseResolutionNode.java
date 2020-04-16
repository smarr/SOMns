package som.interpreter.actors;

import java.util.concurrent.ForkJoinPool;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;

import bd.primitives.nodes.WithContext;
import som.VM;
import som.interpreter.actors.SPromise.Resolution;
import som.interpreter.actors.SPromise.SReplayPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.actors.SPromise.STracingPromise;
import som.interpreter.nodes.nary.QuaternaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.VmSettings;
import tools.concurrency.KomposTrace;
import tools.concurrency.TracingActivityThread;
import tools.replay.ReplayRecord;
import tools.replay.TraceRecord;
import tools.replay.nodes.RecordEventNodes.RecordOneEvent;


@GenerateWrapper
public abstract class AbstractPromiseResolutionNode extends QuaternaryExpressionNode
    implements WithContext<AbstractPromiseResolutionNode, VM> {
  @CompilationFinal private ForkJoinPool actorPool;

  @Child protected WrapReferenceNode   wrapper = WrapReferenceNodeGen.create();
  @Child protected UnaryExpressionNode haltNode;
  @Child protected RecordOneEvent      tracePromiseResolution;
  @Child protected RecordOneEvent      tracePromiseResolutionEnd;
  @Child protected RecordOneEvent      tracePromiseChaining;

  private final ValueProfile whenResolvedProfile = ValueProfile.createClassProfile();

  protected AbstractPromiseResolutionNode() {
    haltNode = insert(SuspendExecutionNodeGen.create(0, null));
    if (VmSettings.UNIFORM_TRACING) {
      tracePromiseResolution = new RecordOneEvent(TraceRecord.PROMISE_RESOLUTION);
      tracePromiseResolutionEnd = new RecordOneEvent(TraceRecord.PROMISE_RESOLUTION_END);
      tracePromiseChaining = new RecordOneEvent(TraceRecord.PROMISE_CHAINED);
    }
  }

  protected AbstractPromiseResolutionNode(final AbstractPromiseResolutionNode node) {
    this();
  }

  @Override
  public AbstractPromiseResolutionNode initialize(final VM vm) {
    actorPool = vm.getActorPool();
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public AbstractPromiseResolutionNode initialize(final SourceSection sourceSection) {
    super.initialize(sourceSection);
    haltNode.initialize(sourceSection);
    return this;
  }

  public abstract Object executeEvaluated(VirtualFrame frame,
      SResolver receiver, Object argument, boolean haltOnResolver,
      boolean haltOnResolution);

  @Override
  public WrapperNode createWrapper(final ProbeNode probe) {
    return new AbstractPromiseResolutionNodeWrapper(this, probe);
  }

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
    if (VmSettings.KOMPOS_TRACING) {
      KomposTrace.promiseChained(
          promiseValue.getPromiseId(), promiseToBeResolved.getPromiseId());
    }

    synchronized (promiseValue) {
      Resolution state = promiseValue.getResolutionStateUnsync();

      if (VmSettings.SENDER_SIDE_REPLAY) {
        ReplayRecord npr = TracingActivityThread.currentThread().getActivity()
                                                .peekNextReplayEvent();
        if (npr.type == TraceRecord.PROMISE_CHAINED) {
          ((SReplayPromise) promiseValue).registerChainedPromiseReplay(
              (SReplayPromise) promiseToBeResolved);
          return;
        }
      }

      if (SPromise.isCompleted(state)) {
        resolvePromise(state, resolver, promiseValue.getValueUnsync(),
            haltOnResolution);
      } else {
        synchronized (promiseToBeResolved) { // TODO: is this really deadlock free?
          if (haltOnResolution || promiseValue.getHaltOnResolution()) {
            promiseToBeResolved.enableHaltOnResolution();
          }

          if (VmSettings.SENDER_SIDE_REPLAY) {
            ReplayRecord npr = TracingActivityThread.currentThread().getActivity()
                                                    .peekNextReplayEvent();
            assert npr.type == TraceRecord.PROMISE_RESOLUTION;
            ((SReplayPromise) promiseToBeResolved).consumeEventsForDelayedResolution();
          }

          if (VmSettings.SENDER_SIDE_TRACING) {
            tracePromiseChaining.record(((STracingPromise) promiseValue).version);
            ((STracingPromise) promiseValue).version++;
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

    resolve(type, wrapper, promise, result, current, actorPool, haltOnResolution,
        whenResolvedProfile, tracePromiseResolution, tracePromiseResolutionEnd);
  }

  public static void resolve(final Resolution type,
      final WrapReferenceNode wrapper, final SPromise promise,
      final Object result, final Actor current, final ForkJoinPool actorPool,
      final boolean haltOnResolution, final ValueProfile whenResolvedProfile,
      final RecordOneEvent tracePromiseResolution2,
      final RecordOneEvent tracePromiseResolutionEnd2) {
    Object wrapped = wrapper.execute(result, promise.owner, current);
    SResolver.resolveAndTriggerListenersUnsynced(type, result, wrapped, promise,
        current, actorPool, haltOnResolution, whenResolvedProfile, tracePromiseResolution2,
        tracePromiseResolutionEnd2);
  }
}
