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
import som.vm.NotYetImplementedException;


@GenerateNodeFactory
@Primitive(primitive = "actorsResolve:with:isBPResolution:")
@Instrumentable(factory = ResolvePromiseNodeWrapper.class)
public abstract class ResolvePromiseNode extends TernaryExpressionNode {

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
  @Specialization(guards = {"resolver.getPromise() != result"})
  public SResolver chainedPromise(final VirtualFrame frame,
      final SResolver resolver, final SPromise result,
      final boolean isBreakpointOnPromiseResolution) {
    assert resolver.assertNotCompleted();
    SPromise promise = resolver.getPromise();

    synchronized (result) {
      if (result.isResolvedUnsync()) {
        normalResolution(frame, resolver, result.getValueUnsync(),
            isBreakpointOnPromiseResolution);
      } else if (result.isErroredUnsync()) {
        CompilerDirectives.transferToInterpreter();
        throw new NotYetImplementedException();
      } else {
        synchronized (promise) { // TODO: is this really deadlock free?
          result.addChainedPromise(promise);
        }
      }
    }

    return resolver;
  }

  protected static boolean notAPromise(final Object result) {
    return !(result instanceof SPromise);
  }

  @Child protected WrapReferenceNode wrapper = WrapReferenceNodeGen.create();

  /**
   * Normal case, when the promise is resolved with a value that's not a promise.
   */
  @Specialization(guards = {"notAPromise(result)"})
  public SResolver normalResolution(final VirtualFrame frame,
      final SResolver resolver, final Object result,
      final boolean isBreakpointOnPromiseResolution) {
    SPromise promise = resolver.getPromise();
    Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();
    Object wrapped = wrapper.execute(result, promise.owner, current);

    SResolver.resolveAndTriggerListenersUnsynced(result, wrapped, promise, current, isBreakpointOnPromiseResolution);
    return resolver;
  }
}
