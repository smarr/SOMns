package somns.interpreter.nodes.specialized.whileloops;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import somns.vmobjects.SBlock;
import somns.vmobjects.SObjectWithClass;


@GenerateNodeFactory
public abstract class WhileFalsePrimitiveNode extends WhilePrimitiveNode {
  public WhileFalsePrimitiveNode(final SourceSection source) {
    super(source, false);
  }

  @Override
  @Specialization
  protected final SObjectWithClass doWhileConditionally(final VirtualFrame frame,
      final SBlock loopCondition, final SBlock loopBody) {
    return (SObjectWithClass) whileNode.executeEvaluated(frame, loopCondition, loopBody);
  }
}
