package somns.vm;

import java.util.List;

import bd.primitives.Specializer;
import somns.VM;
import somns.interpreter.nodes.ExpressionNode;
import somns.vmobjects.SSymbol;


/**
 * Interface for extension jars to implement.
 *
 * The {@code Extension#getFactories()} method returns all primitives provided by the
 * extension. These are then used to create a Newspeak class, similar to {@code vmMirror}.
 */
public interface Extension {
  List<Specializer<VM, ExpressionNode, SSymbol>> getSpecializers();
}
