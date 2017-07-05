package som.interpreter.actors;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.interpreter.actors.SPromise.Resolution;
import som.interpreter.actors.SPromise.SResolver;
import som.primitives.Primitive;


@GenerateNodeFactory
@Primitive(primitive = "actorsError:with:isBPResolver:isBPResolution:", requiresContext = true)
public abstract class ErrorPromiseNode extends AbstractPromiseResolutionNode {

  protected ErrorPromiseNode(final boolean eagWrap, final SourceSection source, final VM vm) { super(eagWrap, source, vm.getActorPool());  }
  protected ErrorPromiseNode(final ErrorPromiseNode node) { super(node); }

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
