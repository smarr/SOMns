package som.vm;

import java.util.List;

import com.oracle.truffle.api.dsl.NodeFactory;

import som.interpreter.nodes.ExpressionNode;


/**
 * Interface for extension jars to implement.
 *
 * The {@code Extension#getFactories()} method returns all primitives provided by the
 * extension. These are then used to create a Newspeak class, similar to {@code vmMirror}.
 */
public interface Extension {
  List<NodeFactory<? extends ExpressionNode>> getFactories();
}
