package som.interpreter.actors;


import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import som.interpreter.SomLanguage;

public abstract class ErrorNode extends AbstractPromiseResolutionNode {
    @CompilerDirectives.CompilationFinal
    boolean initialized = false;

    /**
     * Standard error case, when the promise is errored with a value that's not a promise.
     */
    @Specialization(guards = {"notAPromise(result)"})
    public SPromise.SResolver standardError(final VirtualFrame frame,
                                            final SPromise.SResolver resolver, final Object result, final Object maybeEntry,
                                            final boolean haltOnResolver, final boolean haltOnResolution) {

        if (!initialized) {
            initialized = true;
            this.initialize(SomLanguage.getVM(this));
        }

        SPromise promise = resolver.getPromise();

        if (haltOnResolver || promise.getHaltOnResolver()) {
            haltNode.executeEvaluated(frame, result);
        }

        resolvePromise(SPromise.Resolution.ERRONEOUS, resolver, result, maybeEntry, haltOnResolution);
        return resolver;
    }
}
