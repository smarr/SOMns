package som.primitives;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.constants.KernelObj;
import som.vmobjects.SObject;


public abstract class ObjectSystemPrims {

  @GenerateNodeFactory
  @Primitive(primitive = "kernelObject:")
  public abstract static class KernelObjectPrim extends UnaryExpressionNode {
    public KernelObjectPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization
    public final SObject getKernel(final Object self) {
      return KernelObj.kernel;
    }
  }
}
