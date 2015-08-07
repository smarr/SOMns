package som.compiler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;

import som.compiler.ClassBuilder.ClassDefinitionId;
import som.interpreter.LexicalScope.ClassScope;
import som.interpreter.Method;
import som.interpreter.SNodeFactory;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.FieldNode.FieldWriteNode;
import som.interpreter.nodes.SlotAccessNode;
import som.interpreter.nodes.SlotAccessNode.ClassSlotAccessNode;
import som.interpreter.nodes.SlotAccessNode.SlotReadNode;
import som.interpreter.nodes.SlotAccessNode.SlotWriteNode;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.CachedSlotAccessNode;
import som.interpreter.nodes.dispatch.CachedSlotAccessNode.CachedSlotWriteNode;
import som.interpreter.nodes.dispatch.CachedSlotAccessNode.CheckedCachedSlotAccessNode;
import som.interpreter.nodes.dispatch.CachedSlotAccessNode.CheckedCachedSlotWriteNode;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractWriteFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.UninitializedReadFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.UninitializedWriteFieldNode;
import som.vm.Symbols;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SObjectWithoutFields;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.sun.istack.internal.Nullable;

/**
 * Produced by the Parser, contains all static information on a class that is
 * in the source. Is used to instantiate complete class objects at runtime,
 * which then also have the super class resolved.
 */
public final class ClassDefinition {
  private final SSymbol       name;
  private final SSymbol       primaryFactoryName;
  private final Method        superclassResolution;
  private final HashSet<SlotDefinition> slots;
  private final HashMap<SSymbol, Dispatchable> instanceDispatchable;
  private final HashMap<SSymbol, SInvokable> factoryMethods;
  private final SourceSection sourceSection;
  private final ClassDefinitionId classId;
  private final ClassScope     instanceScope;
  private final ClassScope     classScope;
  private final AccessModifier accessModifier;

  private final boolean allSlotsAreImmutable;
  private final boolean outerScopeIsImmutable;

  @Nullable
  private final LinkedHashMap<SSymbol, ClassDefinition> nestedClassDefinitions;

  public ClassDefinition(final SSymbol name, final SSymbol primaryFactoryName,
      final Method superclassResolution, final HashSet<SlotDefinition> slots,
      final HashMap<SSymbol, Dispatchable> instanceDispatchable,
      final HashMap<SSymbol, SInvokable>   factoryMethods,
      final LinkedHashMap<SSymbol, ClassDefinition> nestedClassDefinitions,
      final ClassDefinitionId classId, final AccessModifier accessModifier,
      final ClassScope instanceScope, final ClassScope classScope,
      final boolean allSlotsAreImmutable, final boolean outerScopeIsImmutable,
      final SourceSection sourceSection) {
    this.name = name;
    this.primaryFactoryName   = primaryFactoryName;
    this.superclassResolution = superclassResolution;
    this.instanceDispatchable = instanceDispatchable;
    this.factoryMethods  = factoryMethods;
    this.nestedClassDefinitions = nestedClassDefinitions;
    this.sourceSection   = sourceSection;
    this.classId         = classId;
    this.accessModifier  = accessModifier;
    this.instanceScope   = instanceScope;
    this.classScope      = classScope;
    this.slots           = slots;

    this.allSlotsAreImmutable  = allSlotsAreImmutable;
    this.outerScopeIsImmutable = outerScopeIsImmutable;
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
    result.setClassDefinition(this);

    // build class class name
    String ccName = name.getString() + " class";

    if (result.getSOMClass() != null) {
      // Initialize the class of the resulting class
      SClass classClass = result.getSOMClass();
      classClass.setDispatchables(classScope.getDispatchables());
      classClass.setName(Symbols.symbolFor(ccName));
      classClass.setClassDefinition(this);
      classClass.setSuperClass(Classes.classClass);

      // they don't have slots, so only the outer context counts
      assert Classes.classClass.instancesAreValues();
      classClass.setInstancesAreValues(outerScopeIsImmutable);
    }

    // Initialize the resulting class
    result.setName(name);

    HashSet<SlotDefinition> instanceSlots = slots != null ? new HashSet<>(slots) : new HashSet<>();
    if (superClass != null) {
      HashSet<SlotDefinition> superSlots = superClass.getInstanceSlots();
      if (superSlots != null) {
        instanceSlots.addAll(superSlots);
      }
    }
    result.setSlots(instanceSlots);
    result.setDispatchables(instanceScope.getDispatchables());
    result.setInstancesAreValues(
        (superClass != null ? superClass.instancesAreValues() : true) &&
        allSlotsAreImmutable && outerScopeIsImmutable);
  }

  public HashMap<SSymbol, SInvokable> getFactoryMethods() {
    return factoryMethods;
  }

  public HashMap<SSymbol, Dispatchable> getInstanceDispatchables() {
    return instanceDispatchable;
  }

  public SClass instantiateClass() {
    CallTarget callTarget = superclassResolution.createCallTarget();
    SClass superClass = (SClass) callTarget.call(Nil.nilObject);
    SClass classObject = instantiateClass(Nil.nilObject, superClass);
    return classObject;
  }

  public SClass instantiateClass(final SObjectWithoutFields outer, final SClass superClass) {
    SClass resultClass = new SClass(outer, Classes.metaclassClass);
    SClass result = new SClass(outer, resultClass);
    initializeClass(result, superClass);
    return result;
  }

  // TODO: need to rename this, it doesn't really fulfill this role anymore
  //       should split out the definition, and the accessor related stuff
  //       perhaps move some of the responsibilities to SlotAccessNode???
  public static class SlotDefinition implements Dispatchable {
    private final SSymbol name;
    protected final AccessModifier modifier;
    private final boolean immutable;
    protected final SourceSection source;

    @CompilationFinal
    protected CallTarget genericAccessTarget;

    public SlotDefinition(final SSymbol name,
        final AccessModifier acccessModifier, final boolean immutable,
        final SourceSection source) {
      this.name      = name;
      this.modifier  = acccessModifier;
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
      if (modifier == AccessModifier.PRIVATE) {
        return new CachedSlotAccessNode(createNode());
      } else {
        return new CheckedCachedSlotAccessNode((SClass) rcvrClass, createNode(), next);
      }
    }

    @Override
    public String typeForErrors() {
      return "slot";
    }

    public FieldWriteNode getWriteNode(final ExpressionNode receiver,
        final ExpressionNode val, final SourceSection source) {
      return SNodeFactory.createFieldWrite(receiver, val, this, source);
    }

    @Override
    public final Object invoke(final Object... arguments) {
      CompilerAsserts.neverPartOfCompilation("call without proper call cache. Find better way if this is performance critical.");
      return this.getCallTarget().call(arguments);
    }

    @Override
    public CallTarget getCallTarget() {
      if (genericAccessTarget != null) { return genericAccessTarget; }

      CompilerDirectives.transferToInterpreterAndInvalidate();

      MethodBuilder builder = new MethodBuilder(true);
      builder.setSignature(name);
      builder.addArgumentIfAbsent("self");

      SInvokable genericAccessMethod = builder.assemble(createNode(), modifier,
          null, source);

      genericAccessTarget = genericAccessMethod.getCallTarget();
      return genericAccessTarget;
    }

    protected SlotAccessNode createNode() {
      SlotReadNode node = new SlotReadNode(
          new UninitializedReadFieldNode(this));
      return node;
    }

    public void setValueDuringBootstrap(final SObject obj, final Object value) {
      obj.setField(this, value);
    }
  }

  // TODO: should not be a subclass of SlotDefinition, should refactor SlotDef. first.
  public static final class SlotMutator extends SlotDefinition {

    private SlotDefinition mainSlot;

    public SlotMutator(final SSymbol name, final AccessModifier acccessModifier,
        final boolean immutable, final SourceSection source,
        final SlotDefinition mainSlot) {
      super(name, acccessModifier, immutable, source);
      assert !immutable;
      this.mainSlot = mainSlot;
    }

    @Override
    public AbstractDispatchNode getDispatchNode(final Object rcvr,
        final Object rcvrClass, final AbstractDispatchNode next) {
      assert rcvrClass instanceof SClass;
      if (modifier == AccessModifier.PRIVATE) {
        return new CachedSlotWriteNode(createWriteNode());
      } else {
        return new CheckedCachedSlotWriteNode((SClass) rcvrClass, createWriteNode(), next);
      }
    }

    @Override
    public CallTarget getCallTarget() {
      if (genericAccessTarget != null) { return genericAccessTarget; }

      CompilerDirectives.transferToInterpreterAndInvalidate();

      MethodBuilder builder = new MethodBuilder(true);
      builder.setSignature(Symbols.symbolFor(getName().getString() + ":"));
      builder.addArgumentIfAbsent("self");
      builder.addArgumentIfAbsent("value");
      SInvokable genericAccessMethod = builder.assemble(
          new SlotWriteNode(createWriteNode()), modifier,
          null, source);

      genericAccessTarget = genericAccessMethod.getCallTarget();
      return genericAccessTarget;
    }

    protected AbstractWriteFieldNode createWriteNode() {
      AbstractWriteFieldNode node = new UninitializedWriteFieldNode(mainSlot);
          new SlotReadNode(new UninitializedReadFieldNode(mainSlot));
      return node;
    }
  }

  /**
   * For the class slots that are generated based on class definitions, we
   * use a separate class to provide a different accessor node.
   */
  public static final class ClassSlotDefinition extends SlotDefinition {
    private final ClassDefinition classDefinition;

    public ClassSlotDefinition(final SSymbol name,
        final ClassDefinition classDefinition) {
      super(name, classDefinition.getAccessModifier(), true,
          classDefinition.getSourceSection());
      this.classDefinition = classDefinition;
    }

    @Override
    protected SlotAccessNode createNode() {
      ClassSlotAccessNode node = new ClassSlotAccessNode(classDefinition,
          new UninitializedReadFieldNode(this),
          new UninitializedWriteFieldNode(this));
      return node;
    }

    @Override
    public String typeForErrors() {
      return "class";
    }
  }

  public ClassDefinition getNestedClassDefinition(final String string) {
    if (nestedClassDefinitions == null) {
      return null;
    }
    return nestedClassDefinitions.get(Symbols.symbolFor(string));
  }

  public ClassDefinition[] getNestedClassDefinitions() {
    return nestedClassDefinitions.values().toArray(
        new ClassDefinition[nestedClassDefinitions.size()]);
  }

  public AccessModifier getAccessModifier() {
    return accessModifier;
  }

  public int getNumberOfSlots() {
    return slots.size();
  }

  public SourceSection getSourceSection() {
    return sourceSection;
  }

  public void addSyntheticInitializerWithoutSuperSendOnlyForThingClass() {
    SSymbol init = ClassBuilder.getInitializerName(Symbols.symbolFor("new"));
    MethodBuilder builder = new MethodBuilder(true);
    builder.setSignature(init);
    builder.addArgumentIfAbsent("self");

    Source source = Source.fromNamedText("self", "Thing>>" + init.getString());
    SourceSection ss = source.createSection(init.getString(), 0, 4);
    SInvokable thingInitNew = builder.assemble(builder.getSelfRead(ss),
        AccessModifier.PROTECTED, Symbols.symbolFor("initializer"), ss);
    instanceDispatchable.put(init, thingInitNew);
  }

  public Object instantiateObject(final Object... args) {
    assert args[0] instanceof SClass;
    SClass classObj = (SClass) args[0];
    Dispatchable factory = classObj.getSOMClass().lookupMessage(
        primaryFactoryName, AccessModifier.PUBLIC);
    return factory.invoke(args);
  }
}
