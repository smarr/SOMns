package som.primitives;

import som.interpreter.Types;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.reflection.AbstractSymbolDispatch;
import som.primitives.reflection.AbstractSymbolDispatchNodeGen;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class MirrorPrims {

  @GenerateNodeFactory
  @Primitive("objNestedClasses:")
  public abstract static class NestedClassesPrim extends UnaryExpressionNode {
    @Specialization
    public final SArray getNestedClasses(final SObject rcvr) {
      SClass[] classes = rcvr.getSOMClass().getNestedClasses(rcvr);
      return SArray.create(classes);
    }
  }

  @GenerateNodeFactory
  @Primitive("obj:respondsTo:")
  public abstract static class RespondsToPrim extends BinaryExpressionNode {
    @Specialization
    public final boolean objectResondsTo(final Object rcvr, final SSymbol selector) {
      CompilerAsserts.neverPartOfCompilation("Uses Types.getClassOf, so, should be specialized in performance cirtical code");
      return Types.getClassOf(rcvr).canUnderstand(selector);
    }
  }

  @GenerateNodeFactory
  @Primitive("objMethods:")
  public abstract static class MethodsPrim extends UnaryExpressionNode {
    @Specialization
    public final SArray getMethod(final Object rcvr) {
      CompilerAsserts.neverPartOfCompilation("Uses Types.getClassOf, so, should be specialized in performance cirtical code");
      SInvokable[] invokables = Types.getClassOf(rcvr).getMethods();
      return SArray.create(invokables);
    }
  }

  @GenerateNodeFactory
  @Primitive("obj:perform:")
  public abstract static class PerformPrim extends BinaryExpressionNode {
    @Child protected AbstractSymbolDispatch dispatch;
    public PerformPrim() { dispatch = AbstractSymbolDispatchNodeGen.create(); }

    @Specialization
    public final Object doPerform(final VirtualFrame frame, final Object rcvr,
        final SSymbol selector) {
      return dispatch.executeDispatch(frame, rcvr, selector, null);
    }
  }

}
