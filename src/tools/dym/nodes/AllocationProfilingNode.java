package tools.dym.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

import som.vmobjects.SObjectWithClass;
import tools.dym.profiles.AllocationProfile;


public class AllocationProfilingNode extends CountingNode<AllocationProfile> {

  public AllocationProfilingNode(final AllocationProfile profile) {
    super(profile);
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    assert result instanceof SObjectWithClass : "WAT? this should only be tracking SObject allocations";
    counter.recordResult((SObjectWithClass) result);
  }
}
