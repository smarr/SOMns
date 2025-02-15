package bd.primitives.nodes;

import com.oracle.truffle.api.nodes.NodeInterface;

import bd.primitives.PrimitiveLoader;


/**
 * Nodes that require a {@link Context}, i.e., execution state from the language or VM for
 * their execution, should implement this interface.
 *
 * <p>
 * The {@link PrimitiveLoader} can use this information to provide the context object
 * automatically on construction.
 *
 * @param <ExprT> the type of node that implements the interface to make return type useful for
 *          fluent methods
 * @param <Context> the type of the context object
 */
public interface WithContext<ExprT, Context> extends NodeInterface {
  ExprT initialize(Context context);
}
