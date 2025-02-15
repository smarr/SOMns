package bd.inlining;

import com.oracle.truffle.api.source.SourceSection;

import bd.basic.ProgramDefinitionError;
import bd.inlining.nodes.Inlinable;


/**
 * Builds a {@link Scope}, typically at source processing time (i.e., source compilation time,
 * which in a Truffle system refers to the time when the Truffle AST is created for execution).
 *
 * <p>A candidate for a concrete scope builder could be for instance the class that creates
 * methods or lambdas when parsing code.
 *
 * @param <This> the concrete type of the builder
 */
public interface ScopeBuilder<This extends ScopeBuilder<This>> {

  /**
   * Introduce a temporary variable for the inlined version of a node.
   *
   * <p>Some code elements require additional temporary variables when they are inlined.
   * An example is the lambda-body for a counting loop, which needs a temporary variable to
   * perform the counting and communicate it to the lambda.
   *
   * @param node the node using a variable that needs to be represented as a new temporary
   * @param source the synthetic source location for the new variable definition
   * @return the introduced variable representing the temporary
   * @throws ProgramDefinitionError when there is a consistency problem caused by introducing
   *           the variable. This is supposed to be used to indicate errors in the user
   *           program.
   */
  Variable<?> introduceTempForInlinedVersion(Inlinable<This> node, SourceSection source)
      throws ProgramDefinitionError;
}
