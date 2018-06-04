package ext;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.dsl.NodeFactory;

import som.interpreter.nodes.ExpressionNode;


public class Extension implements som.vm.Extension {

  @Override
  public List<NodeFactory<? extends ExpressionNode>> getFactories() {
    ArrayList<NodeFactory<? extends ExpressionNode>> result = new ArrayList<>();
    result.addAll(ExtensionPrimsFactory.getFactories());
    return result;
  }
}
