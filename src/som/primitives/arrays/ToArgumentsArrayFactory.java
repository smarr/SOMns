package som.primitives.arrays;

import java.util.List;

import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.nodes.Node;


/**
 * This class is a work around for a JDK 10 javac issue, as discussed here:
 * https://markmail.org/thread/v3kqlmtwfsokfaod.
 *
 * TODO: remove this class, and use normal factory directly
 */
public final class ToArgumentsArrayFactory implements NodeFactory<ToArgumentsArrayNode> {

  public static NodeFactory<ToArgumentsArrayNode> getInstance() {
    return ToArgumentsArrayNodeFactory.getInstance();
  }

  @Override
  public ToArgumentsArrayNode createNode(final Object... arguments) {
    throw new UnsupportedOperationException("Should never be called");
  }

  @Override
  public Class<ToArgumentsArrayNode> getNodeClass() {
    throw new UnsupportedOperationException("Should never be called");
  }

  @Override
  public List<List<Class<?>>> getNodeSignatures() {
    throw new UnsupportedOperationException("Should never be called");
  }

  @Override
  public List<Class<? extends Node>> getExecutionSignature() {
    throw new UnsupportedOperationException("Should never be called");
  }
}
