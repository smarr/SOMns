package som.compiler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import som.VM;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.interpreter.LexicalScope.MixinScope;
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
import som.interpreter.objectstorage.ClassFactory;
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
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.sun.istack.internal.Nullable;


/**
 * Produced by a {@link MixinBuilder}, contains all static information on a
 * mixin that is in the source. Is used to instantiate a {@link ClassFactory}
 * at runtime, which then also has the super class and mixins resolved to be
 * used to instantiate {@link SClass} objects.
 */
public final class MixinDefinition {
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
  private final MixinDefinitionId mixinId;
  private final MixinScope     instanceScope;
  private final MixinScope     classScope;
  private final AccessModifier accessModifier;

  private final boolean allSlotsAreImmutable;
  private final boolean outerScopeIsImmutable;
  private final boolean isModule;

  @Nullable
  private final LinkedHashMap<SSymbol, MixinDefinition> nestedMixinDefinitions;

  public MixinDefinition(final SSymbol name, final SSymbol primaryFactoryName,
      final List<ExpressionNode> initializerBody,
      final MethodBuilder initializerBuilder,
      final SourceSection initializerSource,
      final Method superclassMixinResolution,
      final HashMap<SSymbol, SlotDefinition> slots,
      final HashMap<SSymbol, Dispatchable> instanceDispatchables,
      final HashMap<SSymbol, SInvokable>   factoryMethods,
      final LinkedHashMap<SSymbol, MixinDefinition> nestedMixinDefinitions,
      final MixinDefinitionId mixinId, final AccessModifier accessModifier,
      final MixinScope instanceScope, final MixinScope classScope,
      final boolean allSlotsAreImmutable, final boolean outerScopeIsImmutable,
      final boolean isModule,
      final SourceSection sourceSection) {
    this.name = name;

    this.primaryFactoryName = primaryFactoryName;
    this.initializerBody    = initializerBody;
    this.initializerBuilder = initializerBuilder;
    this.initializerSource  = initializerSource;

    this.superclassMixinResolution = superclassMixinResolution;

    this.instanceDispatchables = instanceDispatchables;
    this.factoryMethods  = factoryMethods;
    this.nestedMixinDefinitions = nestedMixinDefinitions;

    this.sourceSection   = sourceSection;
    this.mixinId         = mixinId;
    this.accessModifier  = accessModifier;
    this.instanceScope   = instanceScope;
    this.classScope      = classScope;
    this.slots           = slots;

    this.allSlotsAreImmutable  = allSlotsAreImmutable;
    this.outerScopeIsImmutable = outerScopeIsImmutable;
    this.isModule = isModule;
  }

  public SSymbol getName() {
    return name;
  }

  // TODO: does this really have to be an invokable?
  //       could it just be the AST, that is than directly used in
  //       the ClassSlotAccessNode?
  public Method getSuperclassAndMixinResolutionInvokable() {
    return superclassMixinResolution;
  }

  public MixinDefinitionId getMixinId() { return mixinId; }

  public void initializeClass(final SClass result, final Object superclassAndMixins) {
    initializeClass(result, superclassAndMixins, false);
  }

  public void initializeClass(final SClass result,
      final Object superclassAndMixins, final boolean isTheValueClass) {
    VM.callerNeedsToBeOptimized("This is supposed to result in a cacheable object, and thus is only the fallback case.");
    ClassFactory factory = createClassFactory(superclassAndMixins, isTheValueClass);
    factory.initializeClass(result);
  }

  public ClassFactory createClassFactory(final Object superclassAndMixins, final boolean isTheValueClass) {
    CompilerAsserts.neverPartOfCompilation();
    VM.callerNeedsToBeOptimized("This is supposed to result in a cacheable object, and thus is only the fallback case.");

    // decode superclass and mixins
    SClass superClass;
    SClass[] mixins;
    if (superclassAndMixins == null || superclassAndMixins instanceof SClass) {
      superClass = (SClass) superclassAndMixins;
      mixins     = new SClass[] {superClass};
    } else {
      mixins     = Arrays.copyOf((Object[]) superclassAndMixins,
                                 ((Object[]) superclassAndMixins).length,
                                 SClass[].class);
      superClass = mixins[0];

      assert mixins.length > 1;
    }

    HashSet<SlotDefinition> instanceSlots = new HashSet<>();
    addSlots(instanceSlots, superClass);
    HashMap<SSymbol, Dispatchable> dispatchables = new HashMap<>();

    boolean mixinsIncludeValue = determineSlotsAndDispatchables(mixins,
        instanceSlots, dispatchables);
    if (instanceSlots.isEmpty()) {
      instanceSlots = null; // let's not hang on to the empty one
    }

    boolean hasOnlyImmutableFields = hasOnlyImmutableFields(instanceSlots);
    boolean instancesAreValues = checkAndConfirmIsValue(superClass,
        mixinsIncludeValue, isTheValueClass, hasOnlyImmutableFields);

    ClassFactory factory = new ClassFactory(name, classScope, this,
        instanceSlots, dispatchables, isModule, mixins, hasOnlyImmutableFields,
        instancesAreValues);

    return factory;
  }

  protected boolean hasOnlyImmutableFields(final HashSet<SlotDefinition> instanceSlots) {
    if (instanceSlots == null) {
      return true;
    }

    boolean hasOnlyImmutableFields = true;
    for (SlotDefinition s : instanceSlots) {
      if (!s.immutable) {
        hasOnlyImmutableFields = false;
        break;
      }
    }
    return hasOnlyImmutableFields;
  }

  private boolean determineSlotsAndDispatchables(final Object[] mixins,
      final HashSet<SlotDefinition> instanceSlots,
      final HashMap<SSymbol, Dispatchable> dispatchables) {
    boolean mixinsIncludeValue = false;
    if (mixins != null) {
      HashMap<SSymbol, SlotDefinition> mixinSlots = new HashMap<>();
      for (int i = 1; i < mixins.length; i++) {
        SClass mixin = (SClass) mixins[i];
        if (mixin == Classes.valueClass) {
          mixinsIncludeValue = true;
        }

        MixinDefinition cdef = mixin.getMixinDefinition();
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

      if (slots != null) {
        instanceSlots.addAll(slots.values());
      }
    } else {
      dispatchables.putAll(instanceScope.getDispatchables());
    }

    if (slots != null) {
      instanceSlots.addAll(slots.values());
    }
    return mixinsIncludeValue;
  }

  private boolean checkAndConfirmIsValue(final SClass superClass,
      final boolean mixinsIncludeValue, final boolean isValueClass, final boolean hasOnlyImmutableFields) {
    boolean superIsValue    = superClass == null ? false : superClass.declaredAsValue();
    boolean declaredAsValue = superIsValue || mixinsIncludeValue || isValueClass;

    if (declaredAsValue && !allSlotsAreImmutable) {
      reportErrorAndExit(": The class ", " is declared as value, but also declared mutable slots");
    }
    if (declaredAsValue && !hasOnlyImmutableFields) {
      reportErrorAndExit(": The class ", " is declared as Value, but superclass or mixins have mutable slots.");
    }
    if (declaredAsValue && !outerScopeIsImmutable) {
      reportErrorAndExit(": The class ", " cannot be a Value, because its enclosing object has mutable fields.");
    }

    return declaredAsValue && allSlotsAreImmutable && outerScopeIsImmutable &&
        hasOnlyImmutableFields;
  }

  @TruffleBoundary
  private void reportErrorAndExit(final String msgPart1, final String msgPart2) {
    String line = sourceSection.getSource().getName() + ":" +
        sourceSection.getStartLine() + ":" + sourceSection.getStartColumn();
    VM.errorExit(line + msgPart1 + name.getString() + msgPart2);
  }

  private SInitializer assembleMixinInitializer(final int mixinId) {
    ExpressionNode body;
    if (initializerBody == null) {
      body = new NilLiteralNode(null);
    } else {
      body = SNodeFactory.createSequence(initializerBody, null);
    }

    return initializerBuilder.splitBodyAndAssembleInitializerAs(
        MixinBuilder.getInitializerName(primaryFactoryName, mixinId),
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

  public HashMap<SSymbol, SInvokable> getFactoryMethods() {
    return factoryMethods;
  }

  public HashMap<SSymbol, Dispatchable> getInstanceDispatchables() {
    return instanceDispatchables;
  }

  public SClass instantiateModuleClass() {
    VM.callerNeedsToBeOptimized("only meant for code loading, which is supposed to be on the slowpath");
    CallTarget callTarget = superclassMixinResolution.createCallTarget();
    SClass superClass = (SClass) callTarget.call(Nil.nilObject);
    SClass classObject = instantiateClass(Nil.nilObject, superClass);
    return classObject;
  }

  public SClass instantiateClass(final SObjectWithClass outer,
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
        return new CheckedCachedSlotAccessNode(((SClass) rcvrClass).getClassFactory(), createNode(), next);
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
      VM.callerNeedsToBeOptimized("call without proper call cache. Find better way if this is performance critical.");
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
        return new CheckedCachedSlotWriteNode(((SClass) rcvrClass).getClassFactory(), createWriteNode(), next);
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
      return node;
    }
  }

  /**
   * For the class slots that are generated based on mixin definitions, we
   * use a separate class to provide a different accessor node.
   */
  public static final class ClassSlotDefinition extends SlotDefinition {
    private final MixinDefinition mixinDefinition;

    public ClassSlotDefinition(final SSymbol name,
        final MixinDefinition mixinDefinition) {
      super(name, mixinDefinition.getAccessModifier(), true,
          mixinDefinition.getSourceSection());
      this.mixinDefinition = mixinDefinition;
    }

    @Override
    protected SlotAccessNode createNode() {
      ClassSlotAccessNode node = new ClassSlotAccessNode(mixinDefinition,
          new UninitializedReadFieldNode(this),
          new UninitializedWriteFieldNode(this));
      return node;
    }

    @Override
    public String typeForErrors() {
      return "class";
    }
  }

  public MixinDefinition getNestedMixinDefinition(final String string) {
    if (nestedMixinDefinitions == null) {
      return null;
    }
    return nestedMixinDefinitions.get(Symbols.symbolFor(string));
  }

  public MixinDefinition[] getNestedMixinDefinitions() {
    return nestedMixinDefinitions.values().toArray(
        new MixinDefinition[nestedMixinDefinitions.size()]);
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
    SSymbol init = MixinBuilder.getInitializerName(Symbols.NEW);
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
    VM.callerNeedsToBeOptimized("Only supposed to be used from Bootstrap.");
    assert args[0] instanceof SClass;
    SClass classObj = (SClass) args[0];
    Dispatchable factory = classObj.getSOMClass().lookupMessage(
        primaryFactoryName, AccessModifier.PUBLIC);
    return factory.invoke(args);
  }
}
