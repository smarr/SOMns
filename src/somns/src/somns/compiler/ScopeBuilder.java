package somns.compiler;

import somns.compiler.Variable.Internal;
import somns.compiler.Variable.Local;
import somns.interpreter.LexicalScope;
import somns.vmobjects.SSymbol;


/**
 * The super class of {@link MixinBuilder} and {@link MethodBuilder}.
 * It manages scope information;
 */
public abstract class ScopeBuilder<LS extends LexicalScope> {
  protected final ScopeBuilder<? extends LexicalScope> outer;

  protected final LS scope;

  protected ScopeBuilder(final ScopeBuilder<?> outer, final LexicalScope outerScope) {
    this.outer = outer;
    this.scope = createScope(outerScope);
  }

  public final ScopeBuilder<?> getOuter() {
    return outer;
  }

  public LS getScope() {
    return scope;
  }

  protected abstract LS createScope(LexicalScope outer);

  public abstract MixinBuilder getMixin();

  public abstract MethodBuilder getMethod();

  protected abstract int getContextLevel(SSymbol varName);

  protected abstract Local getLocal(SSymbol varName);

  protected abstract Variable getVariable(SSymbol varName);

  protected abstract boolean hasArgument(SSymbol varName);

  public abstract Internal getFrameOnStackMarkerVar();

  protected abstract boolean isImmutable();

  public abstract String getName();
}
