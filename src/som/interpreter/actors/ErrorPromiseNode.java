package som.interpreter.actors;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;

import bd.primitives.Primitive;
import bd.tools.nodes.Operation;
import som.interpreter.actors.SPromise.Resolution;
import som.interpreter.actors.SPromise.SResolver;
import tools.dym.Tags.ComplexPrimitiveOperation;


@GenerateNodeFactory
@Primitive(primitive = "actorsError:with:entry:isBPResolver:isBPResolution:")
public abstract class ErrorPromiseNode extends AbstractPromiseResolutionNode
    implements Operation {
  /**
   * Standard error case, when the promise is errored with a value that's not a promise.
   */
  @Specialization(guards = {"notAPromise(result)"})
  public SResolver standardError(final VirtualFrame frame, final SResolver resolver,
      final Object result, final Object maybeEntry, final boolean haltOnResolver,
      final boolean haltOnResolution) {
    SPromise promise = resolver.getPromise();

    if (haltOnResolver || promise.getHaltOnResolver()) {
      haltNode.executeEvaluated(frame, result);
    }

    resolvePromise(Resolution.ERRONEOUS, resolver, result, maybeEntry, haltOnResolution);
    return resolver;
  }

  @Override
  protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
    if (tag == ComplexPrimitiveOperation.class) {
      return true;
    } else {
      return super.hasTagIgnoringEagerness(tag);
    }
  }

  @Override
  public String getOperation() {
    if (getRootNode() instanceof ReceivedRootNode) {
      return "implicitPromiseError";
    } else {
      return "explicitPromiseError";
    }
  }

  @Override
  public int getNumArguments() {
    return 5;
  }
}
