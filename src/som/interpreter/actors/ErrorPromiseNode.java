package som.interpreter.actors;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.interpreter.actors.SPromise.Resolution;
import som.interpreter.actors.SPromise.SResolver;
import som.primitives.Primitive;


@GenerateNodeFactory
@Primitive(primitive = "actorsError:with:isBPResolution:", requiresContext = true)
public abstract class ErrorPromiseNode extends AbstractPromiseResolutionNode {

  protected ErrorPromiseNode(final boolean eagWrap, final SourceSection source, final VM vm) { super(eagWrap, source, vm.getActorPool());  }
  protected ErrorPromiseNode(final ErrorPromiseNode node) { super(node); }

  /**
   * Standard error case, when the promise is errored with a value that's not a promise.
   */
  @Specialization(guards = {"notAPromise(result)"})
  public SResolver standardError(final SResolver resolver, final Object result,
      final boolean isBreakpointOnPromiseResolution) {
    resolvePromise(Resolution.ERRONEOUS, resolver, result, isBreakpointOnPromiseResolution);
    return resolver;
  }
}
