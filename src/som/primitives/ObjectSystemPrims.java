package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.constants.KernelObj;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;


public abstract class ObjectSystemPrims {

  @GenerateNodeFactory
  @Primitive("kernelObject:")
  public abstract static class KernelObjectPrim extends UnaryExpressionNode {
    public KernelObjectPrim(final SourceSection source) { super(source); }

    @Specialization
    public final SObject getKernel(final Object self) {
      return KernelObj.kernel;
    }
  }
}
