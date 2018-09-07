package som.interpreter.actors;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import bd.primitives.Primitive;
import som.interpreter.SArguments;
import som.interpreter.actors.ResolvePromiseNodeFactory.ResolveNodeGen;
import som.interpreter.actors.SPromise.Resolution;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import tools.asyncstacktraces.ShadowStackEntry;


@GenerateNodeFactory
@Primitive(primitive = "actorsResolve:with:")
public abstract class ResolvePromiseNode extends BinaryExpressionNode {

  @Child protected ResolveNode resolve;

  ResolvePromiseNode() {
    resolve = ResolveNodeGen.create(null, null, null, null, null);
  }

  @Specialization
  public SResolver normalResolution(final VirtualFrame frame, final SResolver resolver,
      final Object result) {
    ShadowStackEntry entry = SArguments.getShadowStackEntry(frame);
    return (SResolver) resolve.executeEvaluated(frame, resolver, result, entry, false, false);
  }

  abstract static class ResolveNode extends AbstractPromiseResolutionNode {
    /**
     * Normal case, when the promise is resolved with a value that's not a promise.
     * Here we need to distinguish the explicit promises to ask directly to the promise
     * if a promise resolution breakpoint was set.
     */
    @Specialization(guards = {"notAPromise(result)"})
    public SResolver normalResolution(final VirtualFrame frame,
        final SResolver resolver, final Object result, final ShadowStackEntry entry,
        final boolean haltOnResolver, final boolean haltOnResolution) {
      SPromise promise = resolver.getPromise();

      if (haltOnResolver || promise.getHaltOnResolver()) {
        haltNode.executeEvaluated(frame, result);
      }

      resolvePromise(Resolution.SUCCESSFUL, resolver, result, entry,
          haltOnResolution || promise.getHaltOnResolution());
      return resolver;
    }
  }
}
