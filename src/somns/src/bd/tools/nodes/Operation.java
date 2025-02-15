package bd.tools.nodes;

import com.oracle.truffle.api.nodes.NodeInterface;


/**
 * Interface for tools to identify the operation a node is implementing.
 */
public interface Operation extends NodeInterface {
  /**
   * The name or identifier of an operation implemented by a node.
   * An addition node could return for instance <code>"+"</code>. The name should be
   * understandable by humans, and might be shown in a user interface.
   *
   * @return name of the operation
   */
  String getOperation();

  /**
   * The number of arguments on which the operation depends.
   *
   * @return number of required arguments
   */
  int getNumArguments();
}
