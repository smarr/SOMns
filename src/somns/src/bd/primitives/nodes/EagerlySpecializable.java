package bd.primitives.nodes;

import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.api.source.SourceSection;


/**
 * Marks a node to be eagerly specializable, i.e., either at parse or execution time, this node
 * can be replaced by a node implementing {@link EagerPrimitive}.
 *
 * @param <ExprT> the root type of expressions used by the language
 * @param <Id> the type of the identifiers used for mapping to primitives, typically some form
 *          of interned string construct
 * @param <Context> the type of the context object
 */
public interface EagerlySpecializable<ExprT, Id, Context> extends NodeInterface {

  /**
   * Initialize the node with a source section and whether it was wrapped into a
   * {@link EagerPrimitive}.
   *
   * @param sourceSection corresponding to the lexical location of the node
   * @param eagerlyWrapped true if it was wrapped into a {@link EagerPrimitive}
   * @return itself to be able to chain initialize methods
   */
  ExprT initialize(SourceSection sourceSection, boolean eagerlyWrapped);

  /**
   * Create an eager primitive wrapper, which wraps this node, and return it.
   */
  ExprT wrapInEagerWrapper(Id selector, ExprT[] arguments, Context context);
}
