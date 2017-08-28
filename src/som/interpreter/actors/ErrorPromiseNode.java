package som.interpreter.actors;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import bd.primitives.Primitive;
import som.interpreter.actors.SPromise.Resolution;
import som.interpreter.actors.SPromise.SResolver;


@GenerateNodeFactory
@Primitive(primitive = "actorsError:with:isBPResolver:isBPResolution:")
public abstract class ErrorPromiseNode extends AbstractPromiseResolutionNode {
  /**
   * Standard error case, when the promise is errored with a value that's not a promise.
   */
  @Specialization(guards = {"notAPromise(result)"})
  public SResolver standardError(final VirtualFrame frame, final SResolver resolver,
      final Object result, final boolean haltOnResolver, final boolean haltOnResolution) {
    SPromise promise = resolver.getPromise();

    if (haltOnResolver || promise.getHaltOnResolver()) {
      haltNode.executeEvaluated(frame, result);
    }

    resolvePromise(Resolution.ERRONEOUS, resolver, result, haltOnResolution);
    return resolver;
  }
}
