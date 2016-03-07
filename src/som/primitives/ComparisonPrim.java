package som.primitives;

import som.compiler.Tags;
import som.interpreter.nodes.nary.BinaryBasicOperation;

import com.oracle.truffle.api.source.SourceSection;

import dym.Tagging;


public abstract class ComparisonPrim extends BinaryBasicOperation {
  protected ComparisonPrim(final SourceSection source) {
    super(Tagging.cloneAndAddTags(source, Tags.OP_COMPARISON));
  }
}
