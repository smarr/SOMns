package som.interpreter.nodes.nary;

import com.oracle.truffle.api.source.SourceSection;

import tools.dym.Tags.ComplexPrimitiveOperation;


/**
 * Nodes of this type represent arbitrarily complex operations possibly leading
 * to the execution of user code.
 * This means, these nodes map typically to more than a few native code
 * instructions or cause the execution of arbitrarily complex code.
 */
public abstract class BinaryComplexOperation extends BinaryExpressionNode {
  protected BinaryComplexOperation(final boolean eagWrap, final SourceSection source) {
    super(eagWrap, source);
  }

  protected BinaryComplexOperation(final BinaryComplexOperation node) {
    super(node);
  }

  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == ComplexPrimitiveOperation.class) {
      return true;
    } else {
      return super.isTaggedWithIgnoringEagerness(tag);
    }
  }
}
