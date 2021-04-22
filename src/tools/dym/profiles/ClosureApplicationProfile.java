package tools.dym.profiles;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.nodes.Node;

import som.interpreter.Invokable;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.EagerPrimitiveNode;


public class ClosureApplicationProfile extends Counter {

  private final Node instrumentedNode;

  public ClosureApplicationProfile(final Node instrumentedNode) {
    super(instrumentedNode.getSourceSection());
    this.instrumentedNode = instrumentedNode;
  }

  public Map<Invokable, Integer> getCallTargets() {
    HashMap<Invokable, Integer> result = new HashMap<>();

    DispatchProfile valuePrim = (DispatchProfile) EagerPrimitiveNode.unwrapIfNecessary(
        (ExpressionNode) instrumentedNode);
    valuePrim.collectDispatchStatistics(result);

    return result;
  }
}
