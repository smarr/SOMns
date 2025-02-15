package bd.tools.nodes;

import com.oracle.truffle.api.nodes.NodeInterface;


// TODO: How is Invocation different from Operation? These two are really similar. Should figure out whether we really need two at some point.
/**
 * Nodes implementing this interface represent some kind of invocation.
 * Typically, this would be a method or function call, or some kind of message send.
 *
 * <p>
 * The node implementing this interface should be the high-level node, that has a
 * source section that is useful to represent the invocation in an editor.
 *
 * @param <Id> the type of the identifiers used for mapping to primitives, typically some form
 *          of interned string construct
 */
public interface Invocation<Id> extends NodeInterface {

  /**
   * Return the identifier for the invocation that would be recognized by a developer,
   * for instance when displayed in some tool.
   *
   * @return identifier of the invocation.
   */
  Id getInvocationIdentifier();
}
