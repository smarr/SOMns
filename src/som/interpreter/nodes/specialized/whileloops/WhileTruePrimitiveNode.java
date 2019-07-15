package som.interpreter.nodes.specialized.whileloops;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.vmobjects.SBlock;
import som.vmobjects.SObjectWithClass;


@GenerateNodeFactory
public abstract class WhileTruePrimitiveNode extends WhilePrimitiveNode {
  public WhileTruePrimitiveNode(final SourceSection source) {
    super(source, true);
  }

  @Override
  @Specialization
  protected final SObjectWithClass doWhileConditionally(final VirtualFrame frame,
      final SBlock loopCondition, final SBlock loopBody) {
    return (SObjectWithClass) whileNode.executeEvaluated(frame, loopCondition, loopBody);
  }
}
