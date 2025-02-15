package bd.inlining;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;


/**
 * A {@link Scope} is meant to represent a lexical construct that delineate a set of
 * {@link Variable} definitions that belong together, usually based on the textual/language
 * properties of the implemented language.
 *
 * <p>Many languages use lexical scopes, i.e., grouping of variables based on textual elements.
 * Often such groups are delimited by parentheses or other indicators for textual blocks.
 *
 * <p>Scopes are expected to form a chain from the inner to the outer scopes.
 *
 * @param <This> the concrete type of the scope
 * @param <MethodT> the type for a run-time representation of a method, block, lambda,
 *          typically at the implementation level, not necessarily exposed on the language
 *          level. It is likely a subclass of {@link RootNode}.
 */
public interface Scope<This extends Scope<This, MethodT>, MethodT> {

  /**
   * The set of variables defined by this scope.
   *
   * <p>The set excludes variables that are defined in other scopes, even if they might be
   * logically part of the set from language perspective, perhaps because of nesting scopes.
   *
   * @param <T> the type of the {@link Variable} implementation
   *
   * @return the set of variables defined in this scope
   */
  <T extends Variable<? extends Node>> T[] getVariables();

  /**
   * The scope directly enclosing the current scope.
   *
   * @return the outer scope, or {@code null} if it is already the final scope
   */
  This getOuterScopeOrNull();

  /**
   * Lookup the scope corresponding to the given run-time entity, which often corresponds to a
   * nested method, block, lambda, etc.
   *
   * @param method, the method, block, lambda, etc, for which the scope is to be determined
   * @return a scope, or {@code null}
   */
  This getScope(MethodT method);

  /**
   * A name identifying the scope, used for debugging.
   *
   * @return a human-readable name for debugging
   */
  String getName();
}
