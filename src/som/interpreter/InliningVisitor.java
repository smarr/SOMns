package som.interpreter;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;

import som.compiler.MixinBuilder.MixinDefinitionId;
import som.compiler.Variable;
import som.compiler.Variable.Argument;
import som.compiler.Variable.Local;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.SOMNode;
import som.interpreter.nodes.nary.ExprWithTagsNode;


public final class InliningVisitor implements NodeVisitor {

  public static ExpressionNode doInline(
      final ExpressionNode body,
      final MethodScope inlinedCurrentScope, final int appliesTo,
      final boolean someOuterScopeIsMerged) {
    ExpressionNode inlinedBody = NodeUtil.cloneNode(body);

    return NodeVisitorUtil.applyVisitor(inlinedBody,
        new InliningVisitor(inlinedCurrentScope, appliesTo, someOuterScopeIsMerged));
  }

  protected final MethodScope scope;
  protected final boolean     someOuterScopeIsMerged;

  /**
   * This inliner refers to the block at the contextLevel given here, and
   * thus, needs to apply its transformations to elements referring to that
   * level.
   */
  public final int contextLevel;

  private InliningVisitor(final MethodScope scope, final int appliesTo,
      final boolean someOuterScopeIsMerged) {
    this.scope = scope;
    this.contextLevel = appliesTo;
    this.someOuterScopeIsMerged = someOuterScopeIsMerged;
  }

  public boolean someOuterScopeIsMerged() {
    return someOuterScopeIsMerged;
  }

  public static final class ScopeElement {
    public final Variable var;
    public final int      contextLevel;

    private ScopeElement(final Variable var, final int contextLevel) {
      this.var = var;
      this.contextLevel = contextLevel;
    }
  }

  private ScopeElement getSplitVar(final Variable var, final MethodScope scope,
      final int lvl) {
    for (Variable v : scope.getVariables()) {
      if (v.equals(var)) {
        return new ScopeElement(v, lvl);
      }
    }
    MethodScope outer = scope.getOuterMethodScopeOrNull();
    if (outer == null) {
      throw new IllegalStateException("Couldn't find var: " + var.toString());
    } else {
      return getSplitVar(var, outer, lvl + 1);
    }
  }

  public ScopeElement getSplitVar(final Variable var) {
    return getSplitVar(var, scope, 0);
  }

  /**
   * Get the already split scope for an embedded block.
   */
  public MethodScope getScope(final Method method) {
    return scope.getScope(method);
  }

  public MethodScope getCurrentScope() {
    return scope;
  }

  @Override
  public boolean visit(final Node node) {
    if (node instanceof SOMNode) {
      ((SOMNode) node).replaceAfterScopeChange(this);
    }
    return true;
  }

  public void updateRead(final Variable var, final ExprWithTagsNode node, final int ctxLevel) {
    ScopeElement se = getSplitVar(var);
    if (se.var != var || se.contextLevel < ctxLevel) {
      node.replace(se.var.getReadNode(se.contextLevel, node.getSourceSection()));
    } else {
      assert ctxLevel == se.contextLevel;
    }
  }

  public void updateWrite(final Local var, final ExprWithTagsNode node,
      final ExpressionNode valExpr, final int ctxLevel) {
    ScopeElement se = getSplitVar(var);
    if (se.var != var || se.contextLevel < ctxLevel) {
      node.replace(((Local) se.var).getWriteNode(
          se.contextLevel, valExpr, node.getSourceSection()));
    } else {
      assert ctxLevel == se.contextLevel;
    }
  }

  public void updateSelfRead(final Argument var, final ExprWithTagsNode node,
      final MixinDefinitionId mixin, final int ctxLevel) {
    ScopeElement se = getSplitVar(var);
    if (se.var != var || se.contextLevel < ctxLevel) {
      node.replace(((Argument) se.var).getSelfReadNode(se.contextLevel, mixin,
          node.getSourceSection()));
    } else {
      assert ctxLevel == se.contextLevel;
    }
  }

  public void updateSuperRead(final Argument var, final ExprWithTagsNode node,
      final MixinDefinitionId holder, final boolean classSide, final int ctxLevel) {
    ScopeElement se = getSplitVar(var);
    if (se.var != var || se.contextLevel < ctxLevel) {
      node.replace(((Argument) se.var).getSuperReadNode(
          se.contextLevel, holder, classSide, node.getSourceSection()));
    } else {
      assert ctxLevel == se.contextLevel;
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + scope.getMethod().getName() + "]";
  }
}
