package som.interpreter.actors;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import som.interpreter.SomLanguage;
import som.interpreter.actors.SPromise.Resolution;
import som.interpreter.actors.SPromise.SResolver;


public abstract class ResolveNormallyNode extends AbstractPromiseResolutionNode {
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
