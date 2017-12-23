package tools.superinstructions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;

import bd.nodes.EagerPrimitive;


/**
 * The context collector performs the first step of the super-instruction
 * detection heuristic.
 *
 * <p>
 * Given a map from {@link Node} to {@link TypeCounter} objects, it constructs
 * a map from activation contexts to activation counts.
 */
final class ContextCollector implements NodeVisitor {
  private Map<Node, TypeCounter>       activationCounters;
  private Map<ActivationContext, Long> contexts;

  private static final int CONTEXT_LEVEL = 2;

  ContextCollector(final Map<Node, TypeCounter> activationCounters) {
    this.activationCounters = activationCounters;
    this.contexts = new HashMap<>();
  }

  /**
   * Given a node, recursively construct a trace of the given context length
   * and return it as a {@link List}.
   */
  private List<Object> constructTrace(final Node node, final int contextLevel) {
    if (contextLevel == 0 || node.getParent() == null) {
      // If the contextLevel is 0 or if the node is the tree root,
      // we return a list with just one element: The node class name.
      assert !(node instanceof WrapperNode);
      ArrayList<Object> result = new ArrayList<>();
      result.add(getNodeClass(node));
      return result;
    } else {
      // In any other case, we construct the rightmost entries of the
      // trace and recursively construct the left part after that.
      // First, we determine the child slot index, i.e.
      // the index in which node is located in its parent's slots.
      assert !(node instanceof WrapperNode);

      Node childNode = node;
      Node parent = node.getParent();

      // Need to handle the case in which the direct node parent is a wrapper node.
      if (parent instanceof WrapperNode) {
        childNode = parent;
        parent = parent.getParent();
      }

      assert !(parent instanceof WrapperNode);

      int childIndex = getChildIndex(childNode, parent);
      assert childIndex != -1;

      // Now, we construct the trace suffix:
      // [..., s_{n-1}, C_n]
      // and construct the left part recursively.
      String childClass = getNodeClass(node);
      List<Object> parentTrace = constructTrace(parent, contextLevel - 1);
      parentTrace.add(childIndex);
      parentTrace.add(childClass);
      return parentTrace;
    }
  }

  private static int getChildIndex(final Node childNode, final Node parent) {
    return NodeUtil.findNodeChildren(parent).indexOf(childNode);
  }

  /**
   * Construct an activation context of a given length for a given node and activation result
   * Java class.
   */
  private ActivationContext makeActivationContext(final Node node, final Class<?> javaType,
      final int contextLevel) {
    return new ActivationContext(
        constructTrace(node, contextLevel).toArray(),
        javaType.getName());
  }

  /**
   * Return the node class and specifically handle EagerPrimitive nodes.
   */
  private String getNodeClass(final Node node) {
    // EagerPrimitive nodes get an artificial class name that contains their operation.
    if (node instanceof EagerPrimitive) {
      return "PrimitiveOperation:" + ((EagerPrimitive) node).getOperation();
    } else {
      return node.getClass().getName();
    }
  }

  /**
   * Recursively visit a node and its children and construct activation contexts.
   */
  @Override
  public boolean visit(final Node node) {
    if (node instanceof WrapperNode || node instanceof RootNode) {
      return true;
    }

    TypeCounter activationCounter = activationCounters.get(node);
    if (activationCounter != null) {
      Map<Class<?>, Long> activationsByType = activationCounter.getActivations();

      // Handle each activation result Java type separately.
      for (Class<?> javaType : activationsByType.keySet()) {
        long typeActivations = activationsByType.get(javaType);
        // Construct contexts up to CONTEXT_LEVEL
        for (int level = 0; level <= CONTEXT_LEVEL; level++) {
          ActivationContext context = makeActivationContext(node, javaType, level);
          if (context.getNumberOfClasses() == level + 1) {
            contexts.merge(context, typeActivations, Long::sum);
          } else {
            break;
          }
        }
      }
    }
    return true;
  }

  public Map<ActivationContext, Long> getContexts() {
    return contexts;
  }
}
