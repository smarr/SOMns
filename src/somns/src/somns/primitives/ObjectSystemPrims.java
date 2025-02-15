package somns.primitives;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import somns.interpreter.nodes.nary.UnaryExpressionNode;
import somns.vm.constants.KernelObj;
import somns.vmobjects.SObject;


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
