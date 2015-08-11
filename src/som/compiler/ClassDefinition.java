package som.compiler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

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
import som.interpreter.nodes.literals.NilLiteralNode;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractWriteFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.UninitializedReadFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.UninitializedWriteFieldNode;
import som.vm.Symbols;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SInvokable.SInitializer;
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
  private final List<ExpressionNode> initializerBody;
  private final MethodBuilder initializerBuilder;
  private final SourceSection initializerSource;

  private final Method        superclassMixinResolution;

  private final HashMap<SSymbol, SlotDefinition> slots;
  private final HashMap<SSymbol, Dispatchable> instanceDispatchables;
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
      final List<ExpressionNode> initializerBody,
      final MethodBuilder initializerBuilder,
      final SourceSection initializerSource,
      final Method superclassMixinResolution,
      final HashMap<SSymbol, SlotDefinition> slots,
      final HashMap<SSymbol, Dispatchable> instanceDispatchables,
      final HashMap<SSymbol, SInvokable>   factoryMethods,
      final LinkedHashMap<SSymbol, ClassDefinition> nestedClassDefinitions,
      final ClassDefinitionId classId, final AccessModifier accessModifier,
      final ClassScope instanceScope, final ClassScope classScope,
      final boolean allSlotsAreImmutable, final boolean outerScopeIsImmutable,
      final SourceSection sourceSection) {
    this.name = name;

    this.primaryFactoryName = primaryFactoryName;
    this.initializerBody    = initializerBody;
    this.initializerBuilder = initializerBuilder;
    this.initializerSource  = initializerSource;

    this.superclassMixinResolution = superclassMixinResolution;

    this.instanceDispatchables = instanceDispatchables;
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

  public Method getSuperclassAndMixinResolutionInvokable() {
    return superclassMixinResolution;
  }

  public ClassDefinitionId getClassId() { return classId; }

  public void initializeClass(final SClass result, final Object superclassAndMixins) {
    SClass superClass;
    Object[] mixins;
    if (superclassAndMixins == null || superclassAndMixins instanceof SClass) {
      superClass = (SClass) superclassAndMixins;
      mixins     = null;
    } else {
      mixins     = (Object[]) superclassAndMixins;
      superClass = (SClass) mixins[0];
      assert mixins.length > 1;
    }

    result.setName(name);
    result.setSuperClass(superClass);
    result.setClassDefinition(this);
    initializeClassClass(result);

    HashSet<SlotDefinition> instanceSlots = new HashSet<>();
    addSlots(instanceSlots, superClass);

    HashMap<SSymbol, SlotDefinition> mixinSlots = new HashMap<>();
    HashMap<SSymbol, Dispatchable> dispatchables;

    if (mixins != null) {
      dispatchables = new HashMap<>();
      for (int i = 1; i < mixins.length; i++) {
        SClass mixin = (SClass) mixins[i];
        ClassDefinition cdef = mixin.getClassDefinition();
        if (cdef.slots != null) {
          mixinSlots.putAll(cdef.slots);
        }

        for (Entry<SSymbol, Dispatchable> e : cdef.instanceDispatchables.entrySet()) {
          if (!e.getValue().isInitializer()) {
            dispatchables.put(e.getKey(), e.getValue());
          }
        }

        SInitializer mixinInit = cdef.assembleMixinInitializer(i);
        dispatchables.put(mixinInit.getSignature(), mixinInit);
      }
      instanceSlots.addAll(mixinSlots.values());
      dispatchables.putAll(instanceScope.getDispatchables());
    } else {
      dispatchables = instanceScope.getDispatchables();
    }

    if (slots != null) {
      instanceSlots.addAll(slots.values());
    }

    result.setSlots(instanceSlots);
    result.setDispatchables(dispatchables);

    // TODO: also need to add a check when kernel.Value is mixed in, that the resulting class is a value (structurally, and the dynamic enclosing scope)

    // TODO: needs to be adapted for mixins!!!!
    result.setInstancesAreValues(
        (superClass != null ? superClass.instancesAreValues() : true) &&
        allSlotsAreImmutable && outerScopeIsImmutable);
  }

  private SInitializer assembleMixinInitializer(final int mixinId) {
    ExpressionNode body;
    if (initializerBody == null) {
      body = new NilLiteralNode(null);
    } else {
      body = SNodeFactory.createSequence(initializerBody, null);
    }

    return initializerBuilder.splitBodyAndAssembleInitializerAs(
        ClassBuilder.getInitializerName(primaryFactoryName, mixinId),
        body, AccessModifier.PROTECTED, Symbols.INITIALIZER,
        initializerSource);
  }

  private void addSlots(final HashSet<SlotDefinition> instanceSlots,
      final SClass clazz) {
    if (clazz == null) { return; }

    HashSet<SlotDefinition> slots = clazz.getInstanceSlots();
    if (slots == null) { return; }

    instanceSlots.addAll(slots);
  }

  private void initializeClassClass(final SClass result) {
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
  }

  public HashMap<SSymbol, SInvokable> getFactoryMethods() {
    return factoryMethods;
  }

  public HashMap<SSymbol, Dispatchable> getInstanceDispatchables() {
    return instanceDispatchables;
  }

  public SClass instantiateModuleClass() {
    CallTarget callTarget = superclassMixinResolution.createCallTarget();
    SClass superClass = (SClass) callTarget.call(Nil.nilObject);
    SClass classObject = instantiateClass(Nil.nilObject, superClass);
    return classObject;
  }

  public SClass instantiateClass(final SObjectWithoutFields outer,
      final Object superclassAndMixins) {
    SClass resultClass = new SClass(outer, Classes.metaclassClass);
    SClass result = new SClass(outer, resultClass);
    initializeClass(result, superclassAndMixins);
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
    public final boolean isInitializer() {
      return false;
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
          new SlotWriteNode(createWriteNode()), modifier, null, source);

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
    SSymbol init = ClassBuilder.getInitializerName(Symbols.NEW);
    MethodBuilder builder = new MethodBuilder(true);
    builder.setSignature(init);
    builder.addArgumentIfAbsent("self");

    Source source = Source.fromNamedText("self", "Thing>>" + init.getString());
    SourceSection ss = source.createSection(init.getString(), 0, 4);
    SInvokable thingInitNew = builder.assembleInitializer(builder.getSelfRead(ss),
        AccessModifier.PROTECTED, Symbols.INITIALIZER, ss);
    instanceDispatchables.put(init, thingInitNew);
  }

  public Object instantiateObject(final Object... args) {
    assert args[0] instanceof SClass;
    SClass classObj = (SClass) args[0];
    Dispatchable factory = classObj.getSOMClass().lookupMessage(
        primaryFactoryName, AccessModifier.PUBLIC);
    return factory.invoke(args);
  }
}
