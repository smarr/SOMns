package som.interpreter.actors;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.SPromise.Resolution;
import som.interpreter.actors.SPromise.SResolver;
import som.primitives.Primitive;

@GenerateNodeFactory
@Primitive(primitive = "actorsResolve:with:isBPResolution:")
public abstract class ResolvePromiseNode extends AbstractPromiseResolutionNode {

  protected ResolvePromiseNode(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }
  protected ResolvePromiseNode(final ResolvePromiseNode node) { super(node); }

  /**
   * Normal case, when the promise is resolved with a value that's not a promise.
   */
  @Specialization(guards = {"notAPromise(result)"})
  public SResolver normalResolution(final SResolver resolver, final Object result,
      final boolean isBreakpointOnPromiseResolution) {
    resolvePromise(Resolution.SUCCESSFUL, resolver, result, isBreakpointOnPromiseResolution);
    return resolver;
  }
}
