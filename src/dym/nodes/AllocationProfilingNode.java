package dym.nodes;

import som.vmobjects.SObjectWithClass;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventNode;

import dym.profiles.AllocationProfile;


public class AllocationProfilingNode extends EventNode {
  protected final AllocationProfile profile;

  public AllocationProfilingNode(final AllocationProfile profile) {
    this.profile = profile;
  }

  @Override
  protected void onEnter(final VirtualFrame frame) {
    profile.inc();
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    assert result instanceof SObjectWithClass : "WAT? this should only be tracking SObject allocations";
    profile.recordResult((SObjectWithClass) result);
  }
}
