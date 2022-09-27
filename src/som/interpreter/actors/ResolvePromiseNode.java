package som.interpreter.actors;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;

import bd.primitives.Primitive;
import bd.tools.nodes.Operation;
import som.interpreter.SArguments;
import som.interpreter.SomLanguage;
import som.interpreter.actors.ResolvePromiseNodeFactory.ResolveNodeGen;
import som.interpreter.actors.SPromise.Resolution;
import som.interpreter.actors.SPromise.SResolver;
import som.vm.VmSettings;
import tools.asyncstacktraces.ShadowStackEntry;
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

  @Child protected ResolveNode resolve;

  public ResolvePromiseNode() {
    resolve = ResolveNodeGen.create(null, null, null, null, null);
  }

  @Specialization
  public SResolver normalResolution(final VirtualFrame frame, final SResolver resolver,
      final Object result, final Object maybeEntry,
      final boolean haltOnResolver, final boolean haltOnResolution) {
    ShadowStackEntry entry = SArguments.getShadowStackEntry(frame);
    assert entry != null || !VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE;
    return (SResolver) resolve.executeEvaluated(frame, resolver, result, entry, false, false);
  }

  abstract static class ResolveNode extends AbstractPromiseResolutionNode {
    @CompilationFinal boolean initialized = false;

    /**
     * Normal case, when the promise is resolved with a value that's not a promise.
     * Here we need to distinguish the explicit promises to ask directly to the promise
     * if a promise resolution breakpoint was set.
     */
    @Specialization(guards = {"notAPromise(result)"})
    public SResolver normalResolution(final VirtualFrame frame,
        final SResolver resolver, final Object result, final Object maybeEntry,
        final boolean haltOnResolver, final boolean haltOnResolution) {
      if (!initialized) {
        initialized = true;
        this.initialize(SomLanguage.getVM(this));
      }

      SPromise promise = resolver.getPromise();

      if (haltOnResolver || promise.getHaltOnResolver()) {
        haltNode.executeEvaluated(frame, result);
      }

      resolvePromise(Resolution.SUCCESSFUL, resolver, result, maybeEntry,
          haltOnResolution || promise.getHaltOnResolution());
      return resolver;
    }
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
