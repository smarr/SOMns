package som.interpreter.nodes.nary;

import som.compiler.Tags;
import som.interpreter.nodes.SOMNode;

import com.oracle.truffle.api.source.SourceSection;


/**
 * Nodes of this type represent basic operations such as arithmetics and
 * comparisons. Basic means here, that these nodes are mapping to one or only
 * a few basic operations in an ideal native code mapping.
 */
public abstract class BinaryBasicOperation extends BinaryExpressionNode {
  protected BinaryBasicOperation(final SourceSection source) {
    super(SOMNode.cloneAndAddTags(source, Tags.BASIC_PRIMITIVE_OPERATION));
  }
}
