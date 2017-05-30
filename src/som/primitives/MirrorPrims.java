package som.primitives;

import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.compiler.MixinDefinition;
import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.Types;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.objectstorage.FieldReadNode;
import som.primitives.reflection.AbstractSymbolDispatch;
import som.primitives.reflection.AbstractSymbolDispatchNodeGen;
import som.vm.Symbols;
import som.vm.constants.Classes;
import som.vmobjects.SArray;
import som.vmobjects.SArray.SImmutableArray;
import som.vmobjects.SArray.SMutableArray;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SSymbol;


public abstract class MirrorPrims {

  @GenerateNodeFactory
  @Primitive(primitive = "objNestedClasses:")
  public abstract static class NestedClassesPrim extends UnaryExpressionNode {
    public NestedClassesPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization
    public final SMutableArray getNestedClasses(final SObjectWithClass rcvr) {
      SClass[] classes = rcvr.getSOMClass().getNestedClasses(rcvr);
      return new SMutableArray(classes, Classes.arrayClass);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "obj:respondsTo:")
  public abstract static class RespondsToPrim extends BinaryComplexOperation {
    protected RespondsToPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization
    public final boolean objectResondsTo(final Object rcvr, final SSymbol selector) {
      VM.thisMethodNeedsToBeOptimized("Uses Types.getClassOf, so, should be specialized in performance cirtical code");
      return Types.getClassOf(rcvr).canUnderstand(selector);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "objMethods:")
  public abstract static class MethodsPrim extends UnaryExpressionNode {
    public MethodsPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization
    public final SImmutableArray getMethod(final Object rcvr) {
      VM.thisMethodNeedsToBeOptimized("Uses Types.getClassOf, so, should be specialized in performance cirtical code");
      SInvokable[] is = Types.getClassOf(rcvr).getMethods();
      Object[] invokables = Arrays.copyOf(is, is.length, Object[].class);
      return new SImmutableArray(invokables, Classes.valueArrayClass);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "obj:perform:")
  public abstract static class PerformPrim extends BinaryComplexOperation {
    @Child protected AbstractSymbolDispatch dispatch;
    public PerformPrim(final boolean eagWrap, final SourceSection source) {
      super(eagWrap, source);
      dispatch = AbstractSymbolDispatchNodeGen.create(source);
    }

    @Specialization
    public final Object doPerform(final VirtualFrame frame, final Object rcvr,
        final SSymbol selector) {
      return dispatch.executeDispatch(frame, rcvr, selector, null);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "obj:perform:withArguments:")
  public abstract static class PerformWithArgumentsPrim extends TernaryExpressionNode {
    @Child protected AbstractSymbolDispatch dispatch;
    public PerformWithArgumentsPrim(final boolean eagWrap, final SourceSection source) {
      super(eagWrap, source);
      dispatch = AbstractSymbolDispatchNodeGen.create(source);
    }

    @Specialization
    public final Object doPerform(final VirtualFrame frame, final Object rcvr,
        final SSymbol selector, final SArray  argsArr) {
      return dispatch.executeDispatch(frame, rcvr, selector, argsArr);
    }

    @Override
    public NodeCost getCost() {
      return dispatch.getCost();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "classDefinition:")
  public abstract static class ClassDefinitionPrim extends UnaryExpressionNode {
    public ClassDefinitionPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization
    public final Object getClassDefinition(final SClass rcvr) {
      return rcvr.getMixinDefinition();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "classDefNestedClassDefinitions:")
  public abstract static class NestedClassDefinitionsPrim extends UnaryExpressionNode {
    public NestedClassDefinitionsPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization
    public final Object getClassDefinition(final Object mixinHandle) {
      assert mixinHandle instanceof MixinDefinition;
      MixinDefinition def = (MixinDefinition) mixinHandle;
      Object[] nested = def.getNestedMixinDefinitions();
      return new SImmutableArray(nested, Classes.valueArrayClass);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "classDefName:")
  public abstract static class ClassDefNamePrim extends UnaryExpressionNode {
    public ClassDefNamePrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

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
    public ClassDefMethodsPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

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
      return new SImmutableArray(methods.toArray(new Object[0]),
          Classes.valueArrayClass);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "classDef:hasFactoryMethod:")
  public abstract static class ClassDefHasFactoryMethodPrim extends BinaryComplexOperation {
    protected ClassDefHasFactoryMethodPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization
    public final boolean hasFactoryMethod(final Object mixinHandle,
        final SSymbol selector) {
      assert mixinHandle instanceof MixinDefinition;
      MixinDefinition def = (MixinDefinition) mixinHandle;
      return def.getFactoryMethods().containsKey(selector);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "slot:on:")
  public abstract static class SlotOnPrim extends BinaryComplexOperation {
    protected SlotOnPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization
    public final Object readField(final SSymbol selector, final SObject rcvr, @Cached("cachedNode(selector, rcvr)") final FieldReadNode frn) {

      try {
        return frn.read(rcvr);
      } catch (InvalidAssumptionException e) {
        throw new RuntimeException(e);
      }
    }

    @Specialization
    public final Object readField(final String selector, final SObject rcvr, @Cached("cachedNode(selector, rcvr)") final FieldReadNode frn) {
      try {
        return frn.read(rcvr);
      } catch (InvalidAssumptionException e) {
        throw new RuntimeException(e);
      }
    }

    protected FieldReadNode cachedNode(final SSymbol selector, final SObject rcvr) {
      SlotDefinition slot = (SlotDefinition) Types.getClassOf(rcvr).getMixinDefinition().getInstanceDispatchables().get(selector);
      return FieldReadNode.createRead(slot, rcvr);
    }

    protected FieldReadNode cachedNode(final String selector, final SObject rcvr) {
      SlotDefinition slot = (SlotDefinition) Types.getClassOf(rcvr).getMixinDefinition().getInstanceDispatchables().get(Symbols.symbolFor(selector));
      return FieldReadNode.createRead(slot, rcvr);
    }
  }
}
