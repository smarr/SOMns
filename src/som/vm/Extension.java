package som.vm;

import java.util.List;

import bd.primitives.Specializer;
import som.VM;
import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SSymbol;


/**
 * Interface for extension jars to implement.
 *
 * The {@code Extension#getFactories()} method returns all primitives provided by the
 * extension. These are then used to create a Newspeak class, similar to {@code vmMirror}.
 */
public interface Extension {
  List<Specializer<VM, ExpressionNode, SSymbol>> getSpecializers();
}
