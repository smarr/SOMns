package tools.dym.nodes;

import som.primitives.SizeAndLengthPrim;
import som.primitives.SizeAndLengthPrimFactory;
import som.vmobjects.SArray;
import tools.dym.profiles.ArrayCreationProfile;

import com.oracle.truffle.api.frame.VirtualFrame;


public class ArrayAllocationProfilingNode extends CountingNode<ArrayCreationProfile> {

  @Child protected SizeAndLengthPrim size;

  public ArrayAllocationProfilingNode(final ArrayCreationProfile counter) {
    super(counter);
    size = SizeAndLengthPrimFactory.create(null, null);
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    counter.profileArraySize(size.executeEvaluated((SArray) result));
  }
}
