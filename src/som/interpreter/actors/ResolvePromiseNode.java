package som.interpreter.actors;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;

import bd.primitives.Primitive;
import bd.tools.nodes.Operation;
import som.interpreter.SArguments;
import som.interpreter.actors.SPromise.SResolver;
import som.vm.VmSettings;
import tools.debugger.asyncstacktraces.ShadowStackEntry;
import tools.dym.Tags.ComplexPrimitiveOperation;


@GenerateNodeFactory
@Primitive(primitive = "actorsResolve:with:")
public abstract class ResolvePromiseNode extends AbstractPromiseResolutionNode
    implements Operation {
  /**
   * Normal case, when the promise is resolved with a value that's not a promise.
   * Here we need to distinguish the explicit promises to ask directly to the promise
   * if a promise resolution breakpoint was set.
   */

  @Child protected ResolveNormallyNode resolve;

  public ResolvePromiseNode() {
    resolve = ResolveNormallyNodeGen.create(null, null, null, null, null);
  }

  @Specialization
  public SResolver normalResolution(final VirtualFrame frame, final SResolver resolver,
      final Object result, final Object maybeEntry,
      final boolean haltOnResolver, final boolean haltOnResolution) {
    ShadowStackEntry entry = SArguments.getShadowStackEntry(frame);
    assert entry != null || !VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE;
    return (SResolver) resolve.executeEvaluated(frame, resolver, result, entry, false, false);
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
      return "implicitPromiseResolve";
    } else {
      return "explicitPromiseResolve";
    }
  }

  @Override
  public int getNumArguments() {
    return 5;
  }
}
