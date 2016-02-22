package som.interpreter.nodes.nary;

import som.compiler.Tags;

import com.oracle.truffle.api.source.SourceSection;

import dym.Tagging;


/**
 * Nodes of this type represent basic operations such as arithmetics and
 * comparisons. Basic means here, that these nodes are mapping to one or only
 * a few basic operations in an ideal native code mapping.
 */
public abstract class BinaryBasicOperation extends BinaryExpressionNode {
  private static final String[] basicTags = new String[] {Tags.BASIC_PRIMITIVE_OPERATION};
  private static final String[] basicConflictingTags = new String[] {Tags.UNSPECIFIED_INVOKE};

  protected BinaryBasicOperation(final SourceSection source) {
    super(Tagging.cloneAndUpdateTags(source, basicTags, basicConflictingTags));
  }
}
