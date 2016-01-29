package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.nodes.NodeCost;


public interface DispatchChain {
  int lengthOfDispatchChain();

  class Cost {
    public static NodeCost getCost(final DispatchChain chain) {
      int dispatchChain = chain.lengthOfDispatchChain();
      if (dispatchChain == 0) {
        return NodeCost.UNINITIALIZED;
      } else if (dispatchChain == 1) {
        return NodeCost.MONOMORPHIC;
      } else if (dispatchChain <= AbstractDispatchNode.INLINE_CACHE_SIZE) {
        return NodeCost.POLYMORPHIC;
      } else {
        return NodeCost.MEGAMORPHIC;
      }
    }
  }
}
