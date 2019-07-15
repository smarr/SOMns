package ext;

import java.util.ArrayList;
import java.util.List;

import bd.primitives.PrimitiveLoader;
import bd.primitives.Specializer;
import som.VM;
import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SSymbol;


public class Extension implements som.vm.Extension {

  @Override
  public List<Specializer<VM, ExpressionNode, SSymbol>> getSpecializers() {
    List<Specializer<VM, ExpressionNode, SSymbol>> specializers = new ArrayList<>();

    PrimitiveLoader.addAll(specializers, ExtensionPrimsFactory.getFactories());

    return specializers;
  }
}
