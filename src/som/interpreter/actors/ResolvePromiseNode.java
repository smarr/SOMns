package som.interpreter.actors;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.primitives.Primitive;

@GenerateNodeFactory
@Primitive(primitive = "actorsResolve:with:isBPResolution:")
@Instrumentable(factory = ResolvePromiseNodeWrapper.class)
public abstract class ResolvePromiseNode extends TernaryExpressionNode {

  @Child protected WrapReferenceNode wrapper = WrapReferenceNodeGen.create();
  @Child protected RuinPromiseNode ruiner = RuinPromiseNodeFactory.create(false, getSourceSection(), null, null);

  protected ResolvePromiseNode(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }
  protected ResolvePromiseNode(final ResolvePromiseNode node) { super(node); }

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
    assert resolver.assertNotCompleted();
    SPromise promiseToBeResolved = resolver.getPromise();

    synchronized (promiseValue) {
      if (promiseValue.isResolvedUnsync()) {
        normalResolution(resolver, promiseValue.getValueUnsync(),
           isBreakpointOnPromiseResolution);
      } else if (promiseValue.isErroredUnsync()) {
          CompilerDirectives.transferToInterpreter();
          ruiner.executeEvaluated(frame, resolver, promiseValue.getValueUnsync());
      } else {
        synchronized (promiseToBeResolved) { // TODO: is this really deadlock free?
          promiseToBeResolved.setTriggerResolutionBreakpointOnUnresolvedChainedPromise(isBreakpointOnPromiseResolution);
          promiseValue.addChainedPromise(promiseToBeResolved);
        }
      }
    }

    return resolver;
  }

  protected static boolean notAPromise(final Object result) {
    return !(result instanceof SPromise);
  }

  /**
   * Normal case, when the promise is resolved with a value that's not a promise.
   */
  @Specialization(guards = {"notAPromise(result)"})
  public SResolver normalResolution(final SResolver resolver, final Object result,
      final boolean isBreakpointOnPromiseResolution) {
    SPromise promise = resolver.getPromise();
    Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();

    resolve(wrapper, promise, result, current, isBreakpointOnPromiseResolution);
    return resolver;
  }

  public static void resolve(final WrapReferenceNode wrapper,
      final SPromise promise, final Object result, final Actor current,
      final boolean isBreakpointOnPromiseResolution) {
    Object wrapped = wrapper.execute(result, promise.owner, current);
    SResolver.resolveAndTriggerListenersUnsynced(result, wrapped, promise, current, isBreakpointOnPromiseResolution);
  }
}
