package som.interpreter.nodes.nary;

import som.compiler.Tags;

import com.oracle.truffle.api.source.SourceSection;

import dym.Tagging;


/**
 * Nodes of this type represent arbitrarily complex operations possibly leading
 * to the execution of user code.
 * This means, these nodes map typically to more than a few native code
 * instructions or cause the execution of arbitrarily complex code.
 */
public abstract class BinaryComplexOperation extends BinaryExpressionNode {
  protected BinaryComplexOperation(final SourceSection source) {
    super(Tagging.cloneAndAddTags(source, Tags.COMPLEX_PRIMITIVE_OPERATION));
  }
}
