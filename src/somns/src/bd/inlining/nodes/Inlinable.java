package bd.inlining.nodes;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.api.nodes.RootNode;

import bd.inlining.Inline;
import bd.inlining.ScopeBuilder;


/**
 * <code>Inlinable</code> nodes can be replaced, inlined with arbitrary other nodes.
 *
 * <p>Note, {@link Inlinable} is different from {@link Inline}. Nodes marked
 * with @{@link Inline} are replacing more general nodes, which typically have
 * <code>Inlinable</code> nodes as their children.
 *
 * <p>In most cases, the replacement nodes for an <code>Inlinable</code> node will be at least
 * logically sub-nodes of the inlinable node.
 *
 * <p>An example for an <code>Inlinable</code> node is a lambda (closure, block) that
 * represents for instance the body of a for-each operation. The for-each operation can be
 * annotated with @{@link Inline}, so that it's children, which include the node representing
 * the lambda are then inlined. Concretely, the node to replace the <code>Inlinable</code> node
 * would likely be the {@link RootNode} of the lambda.
 *
 * @param <SB> the concrete type of the used {@link ScopeBuilder}
 */
public interface Inlinable<SB extends ScopeBuilder<SB>> extends NodeInterface {

  /**
   * Inline the give node in the context defined by {@code ScopeBuilder}.
   *
   * @param scopeBuilder defines the context for the inlining
   * @return a new node that represents the inlined behavior
   */
  Node inline(SB scopeBuilder);
}
