package bd.inlining;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.nodes.Node;

import bd.testsetup.IfNodeFactory;
import bd.testsetup.ValueNode;
import bd.testsetup.ValueSpecializedNodeFactory;


class Nodes {

  protected static List<Class<? extends Node>> getInlinableNodes() {
    List<Class<? extends Node>> nodes = new ArrayList<>();

    nodes.add(ValueNode.class);

    return nodes;
  }

  protected static List<NodeFactory<? extends Node>> getInlinableFactories() {
    List<NodeFactory<? extends Node>> factories = new ArrayList<>();

    factories.add(IfNodeFactory.getInstance());
    factories.add(ValueSpecializedNodeFactory.getInstance());

    return factories;
  }
}
