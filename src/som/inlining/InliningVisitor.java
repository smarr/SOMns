package som.inlining;

import com.oracle.truffle.api.nodes.NodeVisitor;

import som.compiler.Variable;
import som.interpreter.LexicalScope.MethodScope;


public abstract class InliningVisitor implements NodeVisitor {

  protected final MethodScope scope;

  protected InliningVisitor(final MethodScope scope) {
    this.scope = scope;
  }

  private Variable getSplitVar(final Variable var, final MethodScope scope) {
    for (Variable v : scope.getVariables()) {
      if (v.equals(var)) {
        return v;
      }
    }
    MethodScope outer = scope.getOuterMethodScopeOrNull();
    if (outer == null) {
      throw new IllegalStateException("Couldn't find var: " + var.toString());
    } else {
      return getSplitVar(var, outer);
    }
  }

  public Variable getSplitVar(final Variable var) {
    return getSplitVar(var, scope);
  }
}
