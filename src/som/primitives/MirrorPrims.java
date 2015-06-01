package som.primitives;

import java.util.ArrayList;

import som.compiler.ClassDefinition;
import som.interpreter.Types;
import som.interpreter.nodes.dispatch.Dispatchable;
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

  @GenerateNodeFactory
  @Primitive("classDefinition:")
  public abstract static class ClassDefinitionPrim extends UnaryExpressionNode {
    @Specialization
    public final Object getClassDefinition(final SClass rcvr) {
      return rcvr.getClassDefinition();
    }
  }

  @GenerateNodeFactory
  @Primitive("classDefNestedClassDefinitions:")
  public abstract static class NestedClassDefinitionsPrim extends UnaryExpressionNode {
    @Specialization
    public final Object getClassDefinition(final Object classDefHandle) {
      assert classDefHandle instanceof ClassDefinition;
      ClassDefinition def = (ClassDefinition) classDefHandle;
      ClassDefinition[] nested = def.getNestedClassDefinitions();
      return SArray.create(nested);
    }
  }

  @GenerateNodeFactory
  @Primitive("classDefName:")
  public abstract static class ClassDefNamePrim extends UnaryExpressionNode {
    @Specialization
    public final SSymbol getName(final Object classDefHandle) {
      assert classDefHandle instanceof ClassDefinition;
      ClassDefinition def = (ClassDefinition) classDefHandle;
      return def.getName();
    }
  }

  @GenerateNodeFactory
  @Primitive("classDefMethods:")
  public abstract static class ClassDefMethodsPrim extends UnaryExpressionNode {
    @Specialization
    public final SArray getName(final Object classDefHandle) {
      assert classDefHandle instanceof ClassDefinition;
      ClassDefinition def = (ClassDefinition) classDefHandle;

      ArrayList<SInvokable> methods = new ArrayList<SInvokable>();
      for (Dispatchable disp : def.getInstanceDispatchables().values()) {
        if (disp instanceof SInvokable) {
          methods.add((SInvokable) disp);
        }
      }
      return SArray.create(methods.toArray(new SInvokable[methods.size()]));
    }
  }

  @GenerateNodeFactory
  @Primitive("classDef:hasFactoryMethod:")
  public abstract static class ClassDefHasFactoryMethodPrim extends BinaryExpressionNode {
    @Specialization
    public final boolean hasFactoryMethod(final Object classDefHandle,
        final SSymbol selector) {
      assert classDefHandle instanceof ClassDefinition;
      ClassDefinition def = (ClassDefinition) classDefHandle;
      return def.getFactoryMethods().containsKey(selector);
    }
  }
}
