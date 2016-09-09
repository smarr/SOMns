package som.primitives;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.interpreter.Types;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;


public class ClassPrims {

  // TODO: move to new mirror class
  @GenerateNodeFactory
  @Primitive(primitive = "mirrorAClassesName:")
  public abstract static class NamePrim extends UnaryExpressionNode {
    public NamePrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization
    public final SAbstractObject doSClass(final SClass receiver) {
      return receiver.getName();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "mirrorClassName:")
  public abstract static class ClassNamePrim extends UnaryExpressionNode {
    public ClassNamePrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization
    public final SAbstractObject doSClass(final Object receiver) {
      VM.thisMethodNeedsToBeOptimized("should specialize, to avoid Types.getClassOf()");
      return Types.getClassOf(receiver).getName();
    }
  }

  @GenerateNodeFactory
  public abstract static class SuperClassPrim extends UnaryExpressionNode {
    public SuperClassPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization
    public final SAbstractObject doSClass(final SClass receiver) {
      return receiver.getSuperClass();
    }
  }
}
