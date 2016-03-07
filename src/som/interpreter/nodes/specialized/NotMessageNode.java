package som.interpreter.nodes.specialized;

import som.compiler.Tags;
import som.interpreter.nodes.nary.UnaryBasicOperation;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import dym.Tagging;


@GenerateNodeFactory
public abstract class NotMessageNode extends UnaryBasicOperation {
  public NotMessageNode(final SourceSection source) {
    super(Tagging.cloneAndAddTags(source, Tags.OP_ARITHMETIC));
  }

  @Specialization
  public final boolean doNot(final VirtualFrame frame, final boolean receiver) {
    return !receiver;
  }
}
