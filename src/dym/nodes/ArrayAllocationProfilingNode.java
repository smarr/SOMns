package dym.nodes;

import som.primitives.SizeAndLengthPrim;
import som.primitives.SizeAndLengthPrimFactory;
import som.vmobjects.SArray;

import com.oracle.truffle.api.frame.VirtualFrame;

import dym.profiles.ArrayCreationProfile;


public class ArrayAllocationProfilingNode extends CountingNode<ArrayCreationProfile> {

  @Child protected SizeAndLengthPrim size;

  public ArrayAllocationProfilingNode(final ArrayCreationProfile counter) {
    super(counter);
    size = SizeAndLengthPrimFactory.create(null);
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    counter.profileArraySize(size.executeEvaluated((SArray) result));
  }
}
