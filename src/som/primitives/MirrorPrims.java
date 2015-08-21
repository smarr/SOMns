package som.primitives;

import java.util.ArrayList;

import som.VM;
import som.compiler.MixinDefinition;
import som.interpreter.Types;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.reflection.AbstractSymbolDispatch;
import som.primitives.reflection.AbstractSymbolDispatchNodeGen;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class MirrorPrims {

  @GenerateNodeFactory
  @Primitive("objNestedClasses:")
  public abstract static class NestedClassesPrim extends UnaryExpressionNode {
    @Specialization
    public final SArray getNestedClasses(final SObjectWithClass rcvr) {
      SClass[] classes = rcvr.getSOMClass().getNestedClasses(rcvr);
      return new SArray(classes);
    }
  }

  @GenerateNodeFactory
  @Primitive("obj:respondsTo:")
  public abstract static class RespondsToPrim extends BinaryExpressionNode {
    @Specialization
    public final boolean objectResondsTo(final Object rcvr, final SSymbol selector) {
      VM.thisMethodNeedsToBeOptimized("Uses Types.getClassOf, so, should be specialized in performance cirtical code");
      return Types.getClassOf(rcvr).canUnderstand(selector);
    }
  }

  @GenerateNodeFactory
  @Primitive("objMethods:")
  public abstract static class MethodsPrim extends UnaryExpressionNode {
    @Specialization
    public final SArray getMethod(final Object rcvr) {
      VM.thisMethodNeedsToBeOptimized("Uses Types.getClassOf, so, should be specialized in performance cirtical code");
      SInvokable[] invokables = Types.getClassOf(rcvr).getMethods();
      return new SArray(invokables);
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
      return rcvr.getMixinDefinition();
    }
  }

  @GenerateNodeFactory
  @Primitive("classDefNestedClassDefinitions:")
  public abstract static class NestedClassDefinitionsPrim extends UnaryExpressionNode {
    @Specialization
    public final Object getClassDefinition(final Object mixinHandle) {
      assert mixinHandle instanceof MixinDefinition;
      MixinDefinition def = (MixinDefinition) mixinHandle;
      MixinDefinition[] nested = def.getNestedMixinDefinitions();
      return new SArray(nested);
    }
  }

  @GenerateNodeFactory
  @Primitive("classDefName:")
  public abstract static class ClassDefNamePrim extends UnaryExpressionNode {
    @Specialization
    public final SSymbol getName(final Object mixinHandle) {
      assert mixinHandle instanceof MixinDefinition;
      MixinDefinition def = (MixinDefinition) mixinHandle;
      return def.getName();
    }
  }

  @GenerateNodeFactory
  @Primitive("classDefMethods:")
  public abstract static class ClassDefMethodsPrim extends UnaryExpressionNode {
    @Specialization
    public final SArray getName(final Object mixinHandle) {
      assert mixinHandle instanceof MixinDefinition;
      MixinDefinition def = (MixinDefinition) mixinHandle;

      ArrayList<SInvokable> methods = new ArrayList<SInvokable>();
      for (Dispatchable disp : def.getInstanceDispatchables().values()) {
        if (disp instanceof SInvokable) {
          methods.add((SInvokable) disp);
        }
      }
      return new SArray(methods.toArray(new SInvokable[methods.size()]));
    }
  }

  @GenerateNodeFactory
  @Primitive("classDef:hasFactoryMethod:")
  public abstract static class ClassDefHasFactoryMethodPrim extends BinaryExpressionNode {
    @Specialization
    public final boolean hasFactoryMethod(final Object mixinHandle,
        final SSymbol selector) {
      assert mixinHandle instanceof MixinDefinition;
      MixinDefinition def = (MixinDefinition) mixinHandle;
      return def.getFactoryMethods().containsKey(selector);
    }
  }
}
