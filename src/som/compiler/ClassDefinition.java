package som.compiler;

import java.util.HashMap;
import java.util.LinkedHashMap;

import som.compiler.ClassBuilder.ClassDefinitionId;
import som.interpreter.LexicalScope.ClassScope;
import som.interpreter.Method;
import som.interpreter.SNodeFactory;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.FieldNode.FieldWriteNode;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.CachedClassSlotAccessNode;
import som.interpreter.nodes.dispatch.CachedSlotAccessNode;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.objectstorage.FieldAccessorNode.UninitializedReadFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.UninitializedWriteFieldNode;
import som.vm.NotYetImplementedException;
import som.vm.Symbols;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SInvokable.SMethod;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.SourceSection;
import com.sun.istack.internal.Nullable;

/**
 * Produced by the Parser, contains all static information on a class that is
 * in the source. Is used to instantiate complete class objects at runtime,
 * which then also have the super class resolved.
 * @author Stefan Marr
 */
public final class ClassDefinition {
  private final SSymbol       name;
  private final Method        superclassResolution;
  private final HashMap<SSymbol, SInvokable> instanceMethods;
  private final HashMap<SSymbol, SInvokable> factoryMethods;
  private final SourceSection sourceSection;
  private final ClassDefinitionId classId;
  private final ClassScope     instanceScope;
  private final ClassScope     classScope;
  private final AccessModifier accessModifier;

  @Nullable
  private final LinkedHashMap<SSymbol, ClassDefinition> nestedClassDefinitions;

  @Nullable
  private final LinkedHashMap<SSymbol, SlotDefinition>  slotDefinitions;

  public ClassDefinition(final SSymbol name,
      final Method superclassResolution,
      final HashMap<SSymbol, SInvokable> instanceMethods,
      final HashMap<SSymbol, SInvokable> factoryMethods,
      final LinkedHashMap<SSymbol, ClassDefinition> nestedClassDefinitions,
      final LinkedHashMap<SSymbol, SlotDefinition> slotDefinitions,
      final ClassDefinitionId classId,
      final AccessModifier accessModifier,
      final ClassScope instanceScope, final ClassScope classScope,
      final SourceSection sourceSection) {
    this.name = name;
    this.superclassResolution = superclassResolution;
    this.instanceMethods = instanceMethods;
    this.factoryMethods  = factoryMethods;
    this.nestedClassDefinitions = nestedClassDefinitions;
    this.slotDefinitions = slotDefinitions;
    this.sourceSection   = sourceSection;
    this.classId         = classId;
    this.accessModifier  = accessModifier;
    this.instanceScope   = instanceScope;
    this.classScope      = classScope;
  }

  public SSymbol getName() {
    return name;
  }

  public Method getSuperclassResolutionInvokable() {
    return superclassResolution;
  }

  public ClassDefinitionId getClassId() { return classId; }

  public void initializeClass(final SClass result, final SClass superClass) {
    result.setSuperClass(superClass);
    result.setClassId(classId);

    // build class class name
    String ccName = name.getString() + " class";

    if (result.getSOMClass() != null) {
      // Initialize the class of the resulting class
      result.getSOMClass().setDispatchables(classScope.getDispatchables());
      result.getSOMClass().setName(Symbols.symbolFor(ccName));
      result.getSOMClass().setClassId(classId);
      result.getSOMClass().setSuperClass(Classes.classClass);
    }

    // Initialize the resulting class
    result.setName(name);
    result.setNumberOfSlots(slotDefinitions.size());
    result.setDispatchables(instanceScope.getDispatchables());
  }

  public HashMap<SSymbol, ? extends Dispatchable> getFactoryMethods() {
    return factoryMethods;
  }

  public HashMap<SSymbol, ? extends Dispatchable> getMethods() {
    return instanceMethods;
  }

  public HashMap<SSymbol, ? extends Dispatchable> getSlots() {
    return slotDefinitions;
  }

  public SClass instantiateClass() {
    CallTarget callTarget = superclassResolution.createCallTarget();
    SClass superClass = (SClass) callTarget.call(Nil.nilObject);
    SClass classObject = instantiateClass(Nil.nilObject, superClass);
    return classObject;
  }

  public SClass instantiateClass(final SAbstractObject outer, final SClass superClass) {
    SClass resultClass = new SClass(outer, Classes.metaclassClass);
    SClass result = new SClass(outer, resultClass);
    initializeClass(result, superClass);
    return result;
  }

  public static class SlotDefinition implements Dispatchable {
    private final SSymbol name;
    protected final int index;
    private final AccessModifier modifier;
    private final boolean immutable;
    private final SourceSection source;

    public SlotDefinition(final SSymbol name,
        final AccessModifier acccessModifier, final int index,
        final boolean immutable, final SourceSection source) {
      this.name      = name;
      this.modifier  = acccessModifier;
      this.index     = index;
      this.immutable = immutable;
      this.source    = source;
    }

    public final SSymbol getName() {
      return name;
    }

    @Override
    public final AccessModifier getAccessModifier() {
      return modifier;
    }

    public final boolean isImmutable() {
      return immutable;
    }

    @Override
    public String toString() {
      String imm = immutable ? ", immut" : "";
      return "SlotDef(" + name.getString()
          + " :" + modifier.toString().toLowerCase() + imm + ")";
    }

    @Override
    public AbstractDispatchNode getDispatchNode(final Object rcvr,
        final Object rcvrClass, final AbstractDispatchNode next) {
      assert rcvrClass instanceof SClass;
      return new CachedSlotAccessNode((SClass) rcvrClass, new UninitializedReadFieldNode(index), next);
    }

    public FieldWriteNode getWriteNode(final ExpressionNode receiver,
        final ExpressionNode val, final SourceSection source) {
      return SNodeFactory.createFieldWrite(receiver, val, index, source);
    }

    @Override
    public final Object invoke(final Object... arguments) {
      throw new NotYetImplementedException();
    }

    @Override
    public final CallTarget getCallTargetIfAvailable() {
      throw new UnsupportedOperationException("Slots don't have CallTargets.");
    }

    public void setValueDuringBootstrap(final SObject obj, final Object value) {
      obj.setField(index, value);
    }
  }

  /**
   * For the class slots that are generated based on class definitions, we
   * use a separate class to provide a different accessor node.
   */
  public static final class ClassSlotDefinition extends SlotDefinition {
    private final ClassDefinition classDefinition;

    public ClassSlotDefinition(final SSymbol name, final int index,
        final ClassDefinition classDefinition) {
      super(name, classDefinition.getAccessModifier(), index, true,
          classDefinition.getSourceSection());
      this.classDefinition = classDefinition;
    }

    @Override
    public AbstractDispatchNode getDispatchNode(final Object rcvr,
        final Object rcvrClass, final AbstractDispatchNode next) {
      return new CachedClassSlotAccessNode(classDefinition,
          (SClass) rcvrClass, new UninitializedReadFieldNode(index),
          new UninitializedWriteFieldNode(index), next);
    }
  }

  public ClassDefinition getEmbeddedClassDefinition(final String string) {
    if (nestedClassDefinitions == null) {
      return null;
    }
    return nestedClassDefinitions.get(Symbols.symbolFor(string));
  }

  public AccessModifier getAccessModifier() {
    return accessModifier;
  }

  public int getNumberOfSlots() {
    if (slotDefinitions == null) {
      return 0;
    }
    return slotDefinitions.size();
  }

  public SourceSection getSourceSection() {
    return sourceSection;
  }

  public void addSyntheticInitializerWithoutSuperSendOnlyForThingClass() {
    SSymbol init = ClassBuilder.getInitializerName(Symbols.symbolFor("new"));
    MethodBuilder builder = new MethodBuilder(true);
    builder.setSignature(init);
    builder.addArgumentIfAbsent("self");

    SMethod thingInitNew = builder.assemble(builder.getSelfRead(null),
        AccessModifier.PROTECTED, Symbols.symbolFor("initializer"), null);
    assert instanceMethods.containsKey(init);
    instanceMethods.put(init, thingInitNew);
  }
}
