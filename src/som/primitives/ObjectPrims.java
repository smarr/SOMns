package som.primitives;

import som.VM;
import som.interpreter.Types;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


public final class ObjectPrims {

  @GenerateNodeFactory
  @Primitive("objClassName:")
  public abstract static class ObjectClassNamePrim extends UnaryExpressionNode {
    @Specialization
    public final SSymbol getName(final Object obj) {
      CompilerAsserts.neverPartOfCompilation("Not yet optimized, need add specializations to remove Types.getClassOf");
      return Types.getClassOf(obj).getName();
    }
  }

  @GenerateNodeFactory
  @Primitive("halt:")
  public abstract static class HaltPrim extends UnaryExpressionNode {
    public HaltPrim() { super(null); }
    @Specialization
    public final Object doSAbstractObject(final Object receiver) {
      VM.errorPrintln("BREAKPOINT");
      return receiver;
    }
  }

  @GenerateNodeFactory
  @Primitive("objClass:")
  public abstract static class ClassPrim extends UnaryExpressionNode {
    @Specialization
    public final SClass doSAbstractObject(final SAbstractObject receiver) {
      return receiver.getSOMClass();
    }

    @Specialization
    public final SClass doObject(final Object receiver) {
      CompilerAsserts.neverPartOfCompilation("Should specialize this if performance critical");
      return Types.getClassOf(receiver);
    }
  }
}
