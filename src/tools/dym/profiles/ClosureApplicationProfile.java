package tools.dym.profiles;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import som.instrumentation.CountingDirectCallNode;
import som.interpreter.Invokable;
import som.interpreter.nodes.ExceptionSignalingNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.EagerPrimitiveNode;
import som.interpreter.nodes.nary.EagerlySpecializableNode;


public class ClosureApplicationProfile extends Counter {

  private final Node instrumentedNode;

  public ClosureApplicationProfile(final SourceSection source, final Node instrumentedNode) {
    super(source);
    this.instrumentedNode = instrumentedNode;
  }

  private void extractCallTargetInfoFromCacheNode(final Node cacheNode,
      final HashMap<Invokable, Integer> results) {
    Node cWithNext = cacheNode;
    boolean foundNext = true;

    // traverse the chain for CachedBlockData nodes
    while (foundNext) {
      foundNext = false;
      for (Node cc : cWithNext.getChildren()) {
        if (cc instanceof CountingDirectCallNode) {
          CountingDirectCallNode callNode = (CountingDirectCallNode) cc;
          Invokable ivk = callNode.getInvokable();
          assert !results.containsKey(ivk);
          results.put(ivk, callNode.getCount());
        } else {
          // this should be the next child
          foundNext = true;
          cWithNext = cc;
        }
      }
    }
  }

  public Map<Invokable, Integer> getCallTargets() {
    assert instrumentedNode instanceof EagerPrimitiveNode;
    EagerlySpecializableNode blockPrim =
        ((EagerPrimitiveNode) instrumentedNode).getPrimitiveNode();

    HashMap<Invokable, Integer> results = new HashMap<>();

    for (Node c : blockPrim.getChildren()) {
      if (c instanceof ExpressionNode || c instanceof ExceptionSignalingNode) {
        continue;
      }

      assert c.getClass().getSimpleName().equals("CachedBlockData");

      extractCallTargetInfoFromCacheNode(c, results);
    }

    return results;
  }
}
