package som.interpreter.actors;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.nary.QuaternaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;
import som.vm.NotYetImplementedException;


@GenerateNodeFactory
@Primitive(primitive = "actorsResolve:with:isBPResolver:isBPResolution:")
@Instrumentable(factory = ResolvePromiseNodeWrapper.class)
public abstract class ResolvePromiseNode extends QuaternaryExpressionNode {
  @Child protected UnaryExpressionNode haltNode;

  protected ResolvePromiseNode(final boolean eagWrap, final SourceSection source) {
    super(eagWrap, source);
    haltNode = insert(SuspendExecutionNodeGen.create(false, source, null));
    VM.insertInstrumentationWrapper(haltNode);
  }

  protected ResolvePromiseNode(final ResolvePromiseNode node) {
    super(node);
    haltNode = insert(SuspendExecutionNodeGen.create(false, node.getSourceSection(), null));
    VM.insertInstrumentationWrapper(haltNode);
  }

  public abstract Object executeEvaluated(VirtualFrame frame, final SResolver receiver, Object argument,
      boolean isBreakpointOnPromiseResolver, final boolean isBreakpointOnPromiseResolution);

  /**
   * To avoid cycles in the promise chain, do nothing when a promise is resolved with itself.
   */
  @Specialization(guards = {"resolver.getPromise() == result"})
  public SResolver selfResolution(final SResolver resolver, final SPromise result,
      final boolean isBreakpointOnPromiseResolver, final boolean isBreakpointOnPromiseResolution) {
    return resolver;
  }

  /**
   * Handle the case that a promise is resolved with another promise, which is not itself.
   */
  @Specialization(guards = {"resolver.getPromise() != result"})
  public SResolver chainedPromise(final VirtualFrame frame, final SResolver resolver, final SPromise result,
      final boolean isBreakpointOnPromiseResolver, final boolean isBreakpointOnPromiseResolution) {
    assert resolver.assertNotCompleted();
    SPromise promise = resolver.getPromise();

    synchronized (result) {
      if (result.isResolvedUnsync()) {
        normalResolution(frame, resolver, result.getValueUnsync(),
            isBreakpointOnPromiseResolver, isBreakpointOnPromiseResolution);
      } else if (result.isErroredUnsync()) {
        CompilerDirectives.transferToInterpreter();
        throw new NotYetImplementedException();
      } else {
        synchronized (promise) { // TODO: is this really deadlock free?
          result.addChainedPromise(promise);
        }
      }
    }

    if (isBreakpointOnPromiseResolver) {
      haltNode.executeEvaluated(frame, result);
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
  public SResolver normalResolution(final VirtualFrame frame, final SResolver resolver, final Object result,
      final boolean isBreakpointOnPromiseResolver, final boolean isBreakpointOnPromiseResolution) {
    SPromise promise = resolver.getPromise();
    Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();
    Object wrapped = wrapper.execute(result, promise.owner, current);

    if (isBreakpointOnPromiseResolver) {
      haltNode.executeEvaluated(frame, result);
    }

    SResolver.resolveAndTriggerListenersUnsynced(result, wrapped, promise, current, isBreakpointOnPromiseResolution);

    return resolver;
  }

}
