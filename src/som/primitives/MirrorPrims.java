package som.primitives;

import java.util.ArrayList;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.compiler.MixinDefinition;
import som.interpreter.Types;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.reflection.AbstractSymbolDispatch;
import som.primitives.reflection.AbstractSymbolDispatchNodeGen;
import som.vm.constants.Classes;
import som.vmobjects.SArray.SImmutableArray;
import som.vmobjects.SArray.SMutableArray;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SSymbol;


public abstract class MirrorPrims {

  @GenerateNodeFactory
  @Primitive(primitive = "objNestedClasses:")
  public abstract static class NestedClassesPrim extends UnaryExpressionNode {
    public NestedClassesPrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final SMutableArray getNestedClasses(final SObjectWithClass rcvr) {
      SClass[] classes = rcvr.getSOMClass().getNestedClasses(rcvr);
      return new SMutableArray(classes, Classes.arrayClass);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "obj:respondsTo:")
  public abstract static class RespondsToPrim extends BinaryComplexOperation {
    protected RespondsToPrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final boolean objectResondsTo(final Object rcvr, final SSymbol selector) {
      VM.thisMethodNeedsToBeOptimized("Uses Types.getClassOf, so, should be specialized in performance cirtical code");
      return Types.getClassOf(rcvr).canUnderstand(selector);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "objMethods:")
  public abstract static class MethodsPrim extends UnaryExpressionNode {
    public MethodsPrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final SImmutableArray getMethod(final Object rcvr) {
      VM.thisMethodNeedsToBeOptimized("Uses Types.getClassOf, so, should be specialized in performance cirtical code");
      SInvokable[] invokables = Types.getClassOf(rcvr).getMethods();
      return new SImmutableArray(invokables, Classes.valueArrayClass);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "obj:perform:")
  public abstract static class PerformPrim extends BinaryComplexOperation {
    @Child protected AbstractSymbolDispatch dispatch;
    public PerformPrim(final SourceSection source) {
      super(false, source);
      dispatch = AbstractSymbolDispatchNodeGen.create(source);
    }

    @Specialization
    public final Object doPerform(final VirtualFrame frame, final Object rcvr,
        final SSymbol selector) {
      return dispatch.executeDispatch(frame, rcvr, selector, null);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "classDefinition:")
  public abstract static class ClassDefinitionPrim extends UnaryExpressionNode {
    public ClassDefinitionPrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final Object getClassDefinition(final SClass rcvr) {
      return rcvr.getMixinDefinition();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "classDefNestedClassDefinitions:")
  public abstract static class NestedClassDefinitionsPrim extends UnaryExpressionNode {
    public NestedClassDefinitionsPrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final Object getClassDefinition(final Object mixinHandle) {
      assert mixinHandle instanceof MixinDefinition;
      MixinDefinition def = (MixinDefinition) mixinHandle;
      MixinDefinition[] nested = def.getNestedMixinDefinitions();
      return new SImmutableArray(nested, Classes.valueArrayClass);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "classDefName:")
  public abstract static class ClassDefNamePrim extends UnaryExpressionNode {
    public ClassDefNamePrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final SSymbol getName(final Object mixinHandle) {
      assert mixinHandle instanceof MixinDefinition;
      MixinDefinition def = (MixinDefinition) mixinHandle;
      return def.getName();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "classDefMethods:")
  public abstract static class ClassDefMethodsPrim extends UnaryExpressionNode {
    public ClassDefMethodsPrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final SImmutableArray getName(final Object mixinHandle) {
      assert mixinHandle instanceof MixinDefinition;
      MixinDefinition def = (MixinDefinition) mixinHandle;

      ArrayList<SInvokable> methods = new ArrayList<SInvokable>();
      for (Dispatchable disp : def.getInstanceDispatchables().values()) {
        if (disp instanceof SInvokable) {
          methods.add((SInvokable) disp);
        }
      }
      return new SImmutableArray(methods.toArray(new SInvokable[methods.size()]),
          Classes.valueArrayClass);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "classDef:hasFactoryMethod:")
  public abstract static class ClassDefHasFactoryMethodPrim extends BinaryComplexOperation {
    protected ClassDefHasFactoryMethodPrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final boolean hasFactoryMethod(final Object mixinHandle,
        final SSymbol selector) {
      assert mixinHandle instanceof MixinDefinition;
      MixinDefinition def = (MixinDefinition) mixinHandle;
      return def.getFactoryMethods().containsKey(selector);
    }
  }
}
