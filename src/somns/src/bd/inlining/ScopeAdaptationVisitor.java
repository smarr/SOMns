package bd.inlining;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;

import bd.inlining.nodes.ScopeReference;


/**
 * A Truffle AST {@link NodeVisitor} that is used to fix up {@link ScopeReference} after any
 * scope changes, for instance caused by inlining or splitting.
 */
public final class ScopeAdaptationVisitor implements NodeVisitor {

  protected final Scope<?, ?> newScope;
  protected final Scope<?, ?> oldScope;

  protected final boolean outerScopeChanged;

  /**
   * This visitor refers to the scope at the contextLevel given here, and thus, needs to apply
   * its transformations to elements referring to that level.
   */
  public final int contextLevel;

  /**
   * Use the visitor to adapt a copy of the given {@code body} to the current scope.
   *
   * @param <N> the type of the returned node
   *
   * @param body an AST that needs to be adapted
   * @param newScope is the scope the body needs to be adapted to
   * @param appliesTo the context level, which needs to be changed
   * @param someOuterScopeIsMerged a flag that can possibly used for optimization to decide
   *          whether a node needs to be adapted
   * @return a copy of {@code body} adapted to the given scope
   */
  public static <N extends Node> N adapt(final N body, final Scope<?, ?> newScope,
      final Scope<?, ?> oldScope,
      final int appliesTo, final boolean someOuterScopeIsMerged,
      final TruffleLanguage<?> language) {
    N inlinedBody = NodeUtil.cloneNode(body);

    return NodeVisitorUtil.applyVisitor(inlinedBody,
        new ScopeAdaptationVisitor(newScope, oldScope, appliesTo, someOuterScopeIsMerged),
        language);
  }

  private ScopeAdaptationVisitor(final Scope<?, ?> newScope, final Scope<?, ?> oldScope,
      final int appliesTo, final boolean outerScopeChanged) {
    if (newScope == null) {
      throw new IllegalArgumentException(
          "InliningVisitor requires a scope, but got newScope==null");
    }
    if (oldScope == null) {
      throw new IllegalArgumentException(
          "InliningVisitor requires a scope, but got oldScope==null");
    }
    this.newScope = newScope;
    this.oldScope = oldScope;
    this.contextLevel = appliesTo;
    this.outerScopeChanged = outerScopeChanged;
  }

  /**
   * @return true if some outer scope was changed, for instance merged with another one.
   */
  public boolean outerScopeChanged() {
    return outerScopeChanged;
  }

  /**
   * The result of a lookup in the scope chain to find a variable and its context level.
   *
   * @param <N> the type of node used for node access, can be very unprecise
   */
  public static final class ScopeElement<N extends Node> {

    /** The variable found by the lookup. */
    public final Variable<N> var;

    /**
     * The context level at which the variable is defined, relative to the start of the lookup.
     */
    public final int contextLevel;

    private ScopeElement(final Variable<N> var, final int contextLevel) {
      this.var = var;
      this.contextLevel = contextLevel;
    }

    @Override
    public String toString() {
      return "ScopeElement[" + var.toString() + ", ctx: " + contextLevel + "]";
    }
  }

  @SuppressWarnings("unchecked")
  private <N extends Node> ScopeElement<N> getSplitVar(final Variable<N> var,
      final Scope<?, ?> scope, final int lvl) {
    for (Variable<? extends Node> v : scope.getVariables()) {
      if (v.equals(var)) {
        return new ScopeElement<>((Variable<N>) v, lvl);
      }
    }

    Scope<?, ?> outer = scope.getOuterScopeOrNull();
    if (outer == null) {
      throw new IllegalStateException("Couldn't find var: " + var.toString());
    } else {
      return getSplitVar(var, outer, lvl + 1);
    }
  }

  /**
   * Get the variable adapted to the current scope.
   *
   * @param var in the un-adapted node
   * @return the adapted version of the variable
   */
  public <N extends Node> ScopeElement<N> getAdaptedVar(final Variable<N> var) {
    return getSplitVar(var, newScope, 0);
  }

  /**
   * Get the adapted scope for an embedded block, lambda, method etc.
   *
   * @param <S> the scope type
   * @param <MethodT> the type of the run-time element representing the scope
   *
   * @param method, the run-time element for which to determine the adapted scope
   *
   * @return the adapted scope for the given method
   */
  @SuppressWarnings("unchecked")
  public <S extends Scope<S, MethodT>, MethodT> S getScope(final MethodT method) {
    return ((S) newScope).getScope(method);
  }

  /**
   * The current scope, which had been adapted before instantiating the visitor.
   *
   * @param <S> the scope type
   * @param <MethodT> the type of the run-time element representing the scope
   *
   * @return the current scope
   */
  @SuppressWarnings("unchecked")
  public <S extends Scope<S, MethodT>, MethodT> S getCurrentScope() {
    return (S) newScope;
  }

  /**
   * Adapt the given node.
   *
   * @return true, if the process should continue
   */
  @Override
  public boolean visit(final Node node) {
    if (node instanceof ScopeReference) {
      ((ScopeReference) node).replaceAfterScopeChange(this);
    }
    return true;
  }

  /**
   * Factory method to update a read node with an appropriate version for the adapted scope.
   *
   * @param <N> the type of the node to be returned
   *
   * @param var the variable accessed by {@code node}
   * @param node the read node
   * @param ctxLevel the context level of the node
   */
  public <N extends Node> void updateRead(final Variable<?> var, final N node,
      final int ctxLevel) {
    ScopeElement<? extends Node> se = getAdaptedVar(var);
    if (se.var != var || se.contextLevel < ctxLevel) {
      node.replace(se.var.getReadNode(se.contextLevel, node.getSourceSection()));
    } else {
      assert ctxLevel == se.contextLevel;
    }
  }

  /**
   * Factory method to update a write node with an appropriate version for the adapted scope.
   *
   * @param <N> the type of the node to be returned
   *
   * @param var the variable accessed by {@code node}
   * @param node the write node
   * @param valExpr the expression that is evaluated to determine the value to be written to
   *          the variable
   * @param ctxLevel the context level of the node
   */
  public <N extends Node> void updateWrite(final Variable<N> var, final N node,
      final N valExpr, final int ctxLevel) {
    ScopeElement<N> se = getAdaptedVar(var);
    if (se.var != var || se.contextLevel < ctxLevel) {
      node.replace(se.var.getWriteNode(se.contextLevel, valExpr, node.getSourceSection()));
    } else {
      assert ctxLevel == se.contextLevel;
    }
  }

  /**
   * Factory method to update a <code>this</code>-read node with an appropriate version for the
   * adapted scope.
   *
   * @param <N> the type of the node to be returned
   *
   * @param var the variable accessed by {@code node}
   * @param node the {@code this} node
   * @param state additional state needed to initialize the adapted node
   * @param ctxLevel the context level of the node
   */
  public <N extends Node> void updateThisRead(final Variable<?> var, final N node,
      final NodeState state, final int ctxLevel) {
    ScopeElement<? extends Node> se = getAdaptedVar(var);
    if (se.var != var || se.contextLevel < ctxLevel) {
      node.replace(se.var.getThisReadNode(se.contextLevel, state, node.getSourceSection()));
    } else {
      assert ctxLevel == se.contextLevel;
    }
  }

  /**
   * Factory method to update a <code>super</code>-read node with an appropriate version for
   * the adapted scope.
   *
   * @param <N> the type of the node to be returned
   *
   * @param var the variable accessed by {@code node}
   * @param node the {@code super} node
   * @param state additional state needed to initialize the adapted node
   * @param ctxLevel the context level of the node
   */
  public <N extends Node> void updateSuperRead(final Variable<?> var, final N node,
      final NodeState state, final int ctxLevel) {
    ScopeElement<? extends Node> se = getAdaptedVar(var);
    if (se.var != var || se.contextLevel < ctxLevel) {
      node.replace(se.var.getSuperReadNode(se.contextLevel, state, node.getSourceSection()));
    } else {
      assert ctxLevel == se.contextLevel;
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + newScope.getName() + "]";
  }
}
