package som.primitives;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.constants.KernelObj;
import som.vmobjects.SObject;


public abstract class ObjectSystemPrims {

  @GenerateNodeFactory
  @Primitive(primitive = "kernelObject:")
  public abstract static class KernelObjectPrim extends UnaryExpressionNode {
    @Specialization
    public final SObject getKernel(final Object self) {
      return KernelObj.kernel;
    }
  }
}
