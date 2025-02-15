package bd.primitives.nodes;

import bd.primitives.Specializer;
import bd.tools.nodes.Operation;


/**
 * Mark a node to be a primitive operation that can be eagerly placed into the AST using the
 * facilities offered by {@link Specializer}. <code>EagerPrimitive</code> nodes are themselves
 * merely wrappers. The actual operation is normally nested inside the node.
 */
public interface EagerPrimitive extends Operation, PreevaluatedExpression {

  /**
   * When replacing a node in the AST, the node might have a <code>tagMark</code> encoding the
   * node's tags.
   *
   * @param tagMark tags to be set in the node
   */
  void setTags(byte tagMark);
}
