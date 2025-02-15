package bd.inlining;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;


/**
 * A {@link Variable} represents a variable most often in the user code, or sometimes internal
 * to the language implementation.
 *
 * <p>Generally, we expect variables to be read or written, but do not require an
 * implementation the writing operation, since variables might be immutable and initialized
 * otherwise.
 *
 * <p>Some special variables, such as <code>this</code> can require extra handling and can thus
 * require the use of special nodes. We provide here factory methods for <code>this</code>-like
 * variables as well as <code>super</code>-like reads.
 *
 * <p>Note that variable access are typically associated with a <code>contextLevel</code>. The
 * precise semantics is specific to your language's use of {@link Scope}s. But generally, we
 * assume that scopes are defined lexically, and a context level of 0 means the local scope,
 * and every increment represents one step outwards in a scope chain.
 *
 * @param <N> the type of nodes expected to be returned for reading variables
 */
public interface Variable<N extends Node> {

  /**
   * Create a node to read the value of this variable.
   *
   * @param contextLevel references the scope in which the variable is defined,
   *          relative to the scope in which the read is done
   * @param source of the read operation
   * @return a node to read this variable
   */
  N getReadNode(int contextLevel, SourceSection source);

  /**
   * Create a node to write to this variable.
   *
   * @param contextLevel references the scope in which the variable is defined,
   *          relative to the scope in which the write is done
   * @param valueExpr is the expression that needs to be evaluated to determine the value,
   *          which is to be written to the variable
   * @param source of the write operation
   * @return a node to write this variable
   */
  default N getWriteNode(final int contextLevel, final N valueExpr,
      final SourceSection source) {
    throw new UnsupportedOperationException(
        "Variable.getWriteNode not supported on this type of variable: "
            + getClass().getSimpleName());
  }

  /**
   * Create a node to read the special <code>this</code> variable.
   *
   * <p>This operation should only be used on variables that are <code>this</code>-like
   * variables.
   *
   * @param contextLevel references the scope in which the variable is defined,
   *          relative to the scope in which the read is done
   * @param state to be used to initialize this node
   * @param source of the read operation
   * @return a node to read <code>this</code>
   */
  default N getThisReadNode(final int contextLevel, final NodeState state,
      final SourceSection source) {
    throw new UnsupportedOperationException(
        "Variable.getThisReadNode not supported on this type of variable: "
            + getClass().getSimpleName());
  }

  /**
   * Create a node to read the special <code>super</code> variable.
   *
   * <p>This operation should only be used on variables that are <code>this</code>-like
   * variables supporting <code>super</code> reads.
   *
   * @param contextLevel references the scope in which the variable is defined,
   *          relative to the scope in which the read is done
   * @param state to be used to initialize this node
   * @param source of the read operation
   * @return a node to read <code>super</code>
   */
  default N getSuperReadNode(final int contextLevel, final NodeState state,
      final SourceSection source) {
    throw new UnsupportedOperationException(
        "Variable.getSuperReadNode not supported on this type of variable: "
            + getClass().getSimpleName());
  }
}
