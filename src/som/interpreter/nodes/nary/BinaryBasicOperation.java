package som.interpreter.nodes.nary;

import com.oracle.truffle.api.instrumentation.Tag;

import tools.dym.Tags.BasicPrimitiveOperation;


/**
 * Nodes of this type represent basic operations such as arithmetics and
 * comparisons. Basic means here, that these nodes are mapping to one or only
 * a few basic operations in an ideal native code mapping.
 */
public abstract class BinaryBasicOperation extends BinaryExpressionNode {
  @Override
  protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
    if (tag == BasicPrimitiveOperation.class) {
      return true;
    } else {
      return super.hasTagIgnoringEagerness(tag);
    }
  }
}
