package som.compiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.sun.istack.internal.Nullable;

import som.VM;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.LexicalScope.MixinScope;
import som.interpreter.Method;
import som.interpreter.SNodeFactory;
import som.interpreter.SomLanguage;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.InstantiationNode.ClassInstantiationNode;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.CachedSlotRead;
import som.interpreter.nodes.dispatch.CachedSlotRead.SlotAccess;
import som.interpreter.nodes.dispatch.CachedSlotWrite;
import som.interpreter.nodes.dispatch.ClassSlotAccessNode;
import som.interpreter.nodes.dispatch.DispatchGuard;
import som.interpreter.nodes.dispatch.DispatchGuard.CheckSObject;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.nodes.literals.NilLiteralNode;
import som.interpreter.objectstorage.ClassFactory;
import som.interpreter.objectstorage.InitializerFieldWrite;
import som.interpreter.objectstorage.StorageLocation;
import som.interpreter.transactions.CachedTxSlotRead;
import som.interpreter.transactions.CachedTxSlotWrite;
import som.vm.Symbols;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SInvokable.SInitializer;
import som.vmobjects.SObject;
import som.vmobjects.SObject.SMutableObject;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SSymbol;
import tools.SourceCoordinate;


/**
 * Produced by a {@link MixinBuilder}, contains all static information on a
 * mixin that is in the source. Is used to instantiate a {@link ClassFactory}
 * at runtime, which then also has the super class and mixins resolved to be
 * used to instantiate {@link SClass} objects.
 */
public final class MixinDefinition {
  private final SSymbol       name;
  private final SourceSection nameSection;

  private final SSymbol              primaryFactoryName;
  private final List<ExpressionNode> initializerBody;
  private final MethodBuilder        initializerBuilder;
  private final SourceSection        initializerSource;

  private final Method superclassMixinResolution;

  private final HashMap<SSymbol, SlotDefinition> slots;
  private final HashMap<SSymbol, Dispatchable>   instanceDispatchables;
  private final HashMap<SSymbol, SInvokable>     factoryMethods;

  private final SourceSection     sourceSection;
  private final MixinDefinitionId mixinId;
  private final MixinScope        instanceScope;
  private final MixinScope        classScope;
  private final AccessModifier    accessModifier;

  private final boolean allSlotsAreImmutable;
  private final boolean outerScopeIsImmutable;
  private final boolean isModule;

  @Nullable private final LinkedHashMap<SSymbol, MixinDefinition> nestedMixinDefinitions;

  public MixinDefinition(final SSymbol name, final SourceSection nameSection,
      final SSymbol primaryFactoryName,
      final List<ExpressionNode> initializerBody,
      final MethodBuilder initializerBuilder,
      final SourceSection initializerSource,
      final Method superclassMixinResolution,
      final HashMap<SSymbol, SlotDefinition> slots,
      final HashMap<SSymbol, Dispatchable> instanceDispatchables,
      final HashMap<SSymbol, SInvokable> factoryMethods,
      final LinkedHashMap<SSymbol, MixinDefinition> nestedMixinDefinitions,
      final MixinDefinitionId mixinId, final AccessModifier accessModifier,
      final MixinScope instanceScope, final MixinScope classScope,
      final boolean allSlotsAreImmutable, final boolean outerScopeIsImmutable,
      final boolean isModule,
      final SourceSection sourceSection) {
    this.name = name;
    this.nameSection = nameSection;

    this.primaryFactoryName = primaryFactoryName;
    this.initializerBody = initializerBody;
    this.initializerBuilder = initializerBuilder;
    this.initializerSource = initializerSource;

    this.superclassMixinResolution = superclassMixinResolution;

    this.instanceDispatchables = instanceDispatchables;
    this.factoryMethods = factoryMethods;
    this.nestedMixinDefinitions = nestedMixinDefinitions;

    this.sourceSection = sourceSection;
    this.mixinId = mixinId;
    this.accessModifier = accessModifier;
    this.instanceScope = instanceScope;
    this.classScope = classScope;
    this.slots = slots;

    this.allSlotsAreImmutable = allSlotsAreImmutable;
    this.outerScopeIsImmutable = outerScopeIsImmutable;
    this.isModule = isModule;
  }

  public SSymbol getName() {
    return name;
  }

  public SourceSection getNameSourceSection() {
    return nameSection;
  }

  /**
   * Used by the SOMns Language Server.
   */
  public MixinDefinition getOuterMixinDefinition() {
    MixinScope outer = instanceScope.getOuterMixin();
    if (outer == null) {
      return null;
    }
    return outer.getMixinDefinition();
  }

  public SSymbol getPrimaryFactorySelector() {
    return primaryFactoryName;
  }

  public boolean isModule() {
    return isModule;
  }

  // TODO: does this really have to be an invokable?
  // could it just be the AST, that is than directly used in
  // the ClassSlotAccessNode?
  public Method getSuperclassAndMixinResolutionInvokable() {
    return superclassMixinResolution;
  }

  public MixinDefinitionId getMixinId() {
    return mixinId;
  }

  public void initializeClass(final SClass result,
      final Object superclassAndMixins) {
    initializeClass(result, superclassAndMixins, false, false, false);
  }

  public void initializeClass(final SClass result,
      final Object superclassAndMixins, final boolean isTheValueClass,
      final boolean isTheTransferObjectClass, final boolean isTheArrayClass) {
    VM.callerNeedsToBeOptimized(
        "This is supposed to result in a cacheable object, and thus is only the fallback case.");
    ClassFactory factory = createClassFactory(superclassAndMixins,
        isTheValueClass, isTheTransferObjectClass, isTheArrayClass);
    if (result.getSOMClass() != null) {
      factory.getClassClassFactory().initializeClass(result.getSOMClass());
    }
    result.setClassGroup(factory.getClassClassFactory());
    factory.initializeClass(result);
  }

  // TODO: do we need to specialize this guard?
  @ExplodeLoop
  public static boolean sameSuperAndMixins(final Object superclassAndMixins,
      final Object cached) {
    if (cached.getClass() != Object[].class) {
      assert cached instanceof SClass;
      assert superclassAndMixins instanceof SClass;

      // TODO: identity comparison? is that stable enough? otherwise, need also
      // to make sure isValue is the same, I think
      boolean result = sameClassConstruction(superclassAndMixins, cached);
      return result;
    }

    if (superclassAndMixins != Object[].class) {
      return false;
    }

    Object[] supMixArr = (Object[]) superclassAndMixins;
    Object[] cachedArr = (Object[]) cached;

    assert supMixArr.length == cachedArr.length; // should be based on lexical
                                                 // info and be compilation
                                                 // constant
    CompilerAsserts.compilationConstant(cachedArr.length);

    for (int i = 0; i < cachedArr.length; i++) {
      // TODO: is this really correct?
      // -> does i == 0 need also to check isValue?
      if (sameClassConstruction(cachedArr[i], supMixArr[i])) {
        return false;
      }
    }
    return true;
  }

  protected static boolean sameClassConstruction(final Object a, final Object b) {
    VM.callerNeedsToBeOptimized(
        "This is recursive, and probably should be specialized to be compilable");
    if (a == b) {
      return true;
    }

    if (a == null || b == null) {
      return false;
    }

    SClass aC = (SClass) a;
    SClass bC = (SClass) b;
    if (aC.getInstanceFactory() == bC.getInstanceFactory()) {
      return true;
    }

    return sameClassConstruction(aC.getSuperClass(), bC.getSuperClass());
  }

  private final ArrayList<ClassFactory> cache = new ArrayList<>(2);

  private ClassFactory getCached(final Object superclassAndMixins) {
    if (superclassAndMixins == null) {
      return null;
    }

    for (ClassFactory cf : cache) {
      SClass[] cached = cf.getSuperclassAndMixins();
      Object comp;
      if (cached.length == 1) {
        comp = cached[0];
      } else {
        comp = cached;
      }
      if (sameSuperAndMixins(superclassAndMixins, comp)) {
        return cf;
      }
    }
    return null;
  }

  public ClassFactory createClassFactory(final Object superclassAndMixins,
      final boolean isTheValueClass, final boolean isTheTransferObjectClass,
      final boolean isTheArrayClass) {
    CompilerAsserts.neverPartOfCompilation();
    VM.callerNeedsToBeOptimized(
        "This is supposed to result in a cacheable object, and thus is only the fallback case.");

    ClassFactory cached = getCached(superclassAndMixins);
    if (cached != null) {
      return cached;
    }

    // decode superclass and mixins
    SClass superClass;
    SClass[] mixins;
    if (superclassAndMixins == null || superclassAndMixins instanceof SClass) {
      superClass = (SClass) superclassAndMixins;
      mixins = new SClass[] {superClass};
    } else {
      mixins = Arrays.copyOf((Object[]) superclassAndMixins,
          ((Object[]) superclassAndMixins).length,
          SClass[].class);
      superClass = mixins[0];

      assert mixins.length > 1;
    }

    HashSet<SlotDefinition> instanceSlots = new HashSet<>();
    addSlots(instanceSlots, superClass);
    HashMap<SSymbol, Dispatchable> dispatchables = new HashMap<>();

    boolean[] mixinInfo = determineSlotsAndDispatchables(mixins,
        instanceSlots, dispatchables);
    boolean mixinsIncludeValue = mixinInfo[0];
    boolean mixinsIncludeTransferObject = mixinInfo[1];

    if (instanceSlots.isEmpty()) {
      instanceSlots = null; // let's not hang on to the empty one
    }

    boolean hasOnlyImmutableFields = hasOnlyImmutableFields(instanceSlots);
    boolean instancesAreValues = checkAndConfirmIsValue(superClass,
        mixinsIncludeValue, isTheValueClass, hasOnlyImmutableFields);
    boolean instancesAreTransferObjects = checkIsTransferObject(superClass,
        mixinsIncludeTransferObject, isTheTransferObjectClass);
    boolean instancesAreArrays = checkIsArray(superClass, isTheArrayClass);

    ClassFactory classClassFactory = new ClassFactory(
        Symbols.symbolFor(name.getString() + " class"), this, null,
        classScope.getDispatchables(), isModule, false, false,
        new SClass[] {Classes.classClass}, true,
        // TODO: not passing a ClassFactory of the meta class here is incorrect,
        // might not matter in practice
        null);

    ClassFactory classFactory = new ClassFactory(name, this,
        instanceSlots, dispatchables, instancesAreValues,
        instancesAreTransferObjects, instancesAreArrays,
        mixins, hasOnlyImmutableFields,
        classClassFactory);

    cache.add(classFactory);

    return classFactory;
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

  private boolean[] determineSlotsAndDispatchables(final Object[] mixins,
      final HashSet<SlotDefinition> instanceSlots,
      final HashMap<SSymbol, Dispatchable> dispatchables) {
    boolean mixinsIncludeValue = false;
    boolean mixinsIncludeTransferObject = false;

    if (mixins != null) {
      HashMap<SSymbol, SlotDefinition> mixinSlots = new HashMap<>();
      for (int i = 1; i < mixins.length; i++) {
        SClass mixin = (SClass) mixins[i];
        if (mixin == Classes.valueClass) {
          mixinsIncludeValue = true;
        } else if (mixin == Classes.transferClass) {
          mixinsIncludeTransferObject = true;
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
    return new boolean[] {mixinsIncludeValue, mixinsIncludeTransferObject};
  }

  private boolean checkAndConfirmIsValue(final SClass superClass,
      final boolean mixinsIncludeValue, final boolean isValueClass,
      final boolean hasOnlyImmutableFields) {
    boolean superIsValue = superClass == null ? false : superClass.declaredAsValue();
    boolean declaredAsValue = superIsValue || mixinsIncludeValue || isValueClass;

    if (declaredAsValue && !allSlotsAreImmutable) {
      reportErrorAndExit(": The class ",
          " is declared as value, but also declared mutable slots");
    }
    if (declaredAsValue && !hasOnlyImmutableFields) {
      reportErrorAndExit(": The class ",
          " is declared as Value, but superclass or mixins have mutable slots.");
    }
    if (declaredAsValue && !outerScopeIsImmutable) {
      reportErrorAndExit(": The class ",
          " cannot be a Value, because its enclosing object has mutable fields.");
    }

    return declaredAsValue && allSlotsAreImmutable && outerScopeIsImmutable &&
        hasOnlyImmutableFields;
  }

  private boolean checkIsTransferObject(final SClass superClass,
      final boolean mixinsIncludeTransferObject, final boolean isTransferObjectClass) {
    boolean superIsValue = superClass == null ? false : superClass.isTransferObject();
    boolean declaredAsValue =
        superIsValue || mixinsIncludeTransferObject || isTransferObjectClass;

    return declaredAsValue;
  }

  private boolean checkIsArray(final SClass superClass, final boolean isTheArrayClass) {
    boolean superIsArray = superClass == null ? false : superClass.isArray();
    return isTheArrayClass || superIsArray;
  }

  @TruffleBoundary
  private void reportErrorAndExit(final String msgPart1, final String msgPart2) {
    String line = sourceSection.getSource().getName()
        + SourceCoordinate.getLocationQualifier(sourceSection);
    initializerBuilder.getLanguage().getVM()
                      .errorExit(line + msgPart1 + name.getString() + msgPart2);
  }

  private SInitializer assembleMixinInitializer(final int mixinId) {
    ExpressionNode body;
    if (initializerBody == null) {
      body = new NilLiteralNode().initialize(initializerSource);
    } else {
      body = SNodeFactory.createSequence(initializerBody, initializerSource);
    }

    return initializerBuilder.splitBodyAndAssembleInitializerAs(
        MixinBuilder.getInitializerName(primaryFactoryName, mixinId),
        body, AccessModifier.PROTECTED, initializerSource);
  }

  private void addSlots(final HashSet<SlotDefinition> instanceSlots,
      final SClass clazz) {
    if (clazz == null) {
      return;
    }

    HashSet<SlotDefinition> slots = clazz.getInstanceSlots();
    if (slots == null) {
      return;
    }

    instanceSlots.addAll(slots);
  }

  public HashMap<SSymbol, SInvokable> getFactoryMethods() {
    return factoryMethods;
  }

  public HashMap<SSymbol, SlotDefinition> getSlots() {
    return slots;
  }

  public HashMap<SSymbol, Dispatchable> getInstanceDispatchables() {
    return instanceDispatchables;
  }

  public SClass instantiateModuleClass() {
    VM.callerNeedsToBeOptimized(
        "only meant for code loading, which is supposed to be on the slowpath");
    CallTarget callTarget = superclassMixinResolution.createCallTarget();
    SClass superClass = (SClass) callTarget.call(Nil.nilObject);
    SClass classObject = instantiateClass(Nil.nilObject, superClass);
    return classObject;
  }

  public SClass instantiateClass(final SObjectWithClass outer,
      final Object superclassAndMixins) {
    ClassFactory factory = createClassFactory(superclassAndMixins,
        false, false, false);
    return ClassInstantiationNode.instantiate(outer, factory);
  }

  // TODO: need to rename this, it doesn't really fulfill this role anymore
  // should split out the definition, and the accessor related stuff
  // perhaps move some of the responsibilities to SlotAccessNode???
  public static class SlotDefinition implements Dispatchable {
    private final SSymbol          name;
    protected final AccessModifier modifier;
    private final boolean          immutable;
    protected final SourceSection  source;

    @CompilationFinal protected CallTarget genericAccessTarget;

    public SlotDefinition(final SSymbol name,
        final AccessModifier acccessModifier, final boolean immutable,
        final SourceSection source) {
      this.name = name;
      this.modifier = acccessModifier;
      this.immutable = immutable;
      this.source = source;
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
    public AbstractDispatchNode getDispatchNode(final Object receiver,
        final Object firstArg, final AbstractDispatchNode next,
        final boolean forAtomic) {
      SObject rcvr = (SObject) receiver;
      StorageLocation loc = rcvr.getObjectLayout().getStorageLocation(this);
      boolean isSet = loc.isSet(rcvr);

      CachedSlotRead read =
          createNode(loc, DispatchGuard.createSObjectCheck(rcvr), next, isSet);

      if (forAtomic && rcvr instanceof SMutableObject &&
          getAccessType() == SlotAccess.FIELD_READ) {
        return new CachedTxSlotRead(getAccessType(), read,
            DispatchGuard.createSObjectCheck(rcvr), next);
      } else {
        return read;
      }
    }

    @Override
    public String typeForErrors() {
      return "slot";
    }

    public InitializerFieldWrite getInitializerWriteNode(final ExpressionNode receiver,
        final ExpressionNode val, final SourceSection source) {
      return SNodeFactory.createFieldWrite(receiver, val, this, source);
    }

    @Override
    public Object invoke(final IndirectCallNode call, final Object[] arguments) {
      VM.callerNeedsToBeOptimized(
          "call without proper call cache. Find better way if this is performance critical.");
      assert arguments.length == 1;
      SObject rcvr = (SObject) arguments[0];
      return rcvr.readSlot(this);
    }

    protected CachedSlotRead createNode(final StorageLocation loc,
        final CheckSObject guard, final AbstractDispatchNode next,
        final boolean isSet) {
      return loc.getReadNode(getAccessType(), guard, next, isSet);
    }

    protected SlotAccess getAccessType() {
      return SlotAccess.FIELD_READ;
    }

    public void setValueDuringBootstrap(final SObject obj, final Object value) {
      obj.writeSlot(this, value);
    }

    public SourceSection getSourceSection() {
      return source;
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
    public AbstractDispatchNode getDispatchNode(final Object receiver,
        final Object firstArg, final AbstractDispatchNode next, final boolean forAtomic) {
      SObject rcvr = (SObject) receiver;
      StorageLocation loc = rcvr.getObjectLayout().getStorageLocation(mainSlot);
      boolean isSet = loc.isSet(rcvr);
      CachedSlotWrite write =
          loc.getWriteNode(mainSlot, DispatchGuard.createSObjectCheck(rcvr), next, isSet);

      if (forAtomic) {
        return new CachedTxSlotWrite(write,
            DispatchGuard.createSObjectCheck(rcvr), next);
      } else {
        return write;
      }
    }

    @Override
    public Object invoke(final IndirectCallNode call, final Object[] arguments) {
      VM.callerNeedsToBeOptimized(
          "call without proper call cache. Find better way if this is performance critical.");
      SObject rcvr = (SObject) arguments[0];
      rcvr.writeSlot(this, arguments[1]);
      return rcvr;
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
    protected CachedSlotRead createNode(final StorageLocation loc,
        final CheckSObject guard, final AbstractDispatchNode next,
        final boolean isSet) {
      CachedSlotRead read = super.createNode(loc, guard, next, isSet);
      CachedSlotWrite write = loc.getWriteNode(this, guard, next, isSet);

      ClassSlotAccessNode node = new ClassSlotAccessNode(mixinDefinition,
          read, write);
      return node;
    }

    @Override
    protected SlotAccess getAccessType() {
      return SlotAccess.CLASS_READ;
    }

    @Override
    public Object invoke(final IndirectCallNode call, final Object[] arguments) {
      VM.callerNeedsToBeOptimized("this should not be on the compiled-code path");
      assert arguments.length == 1;
      SObject rcvr = (SObject) arguments[0];
      Object result = rcvr.readSlot(this);
      assert result != null;
      if (result != Nil.nilObject) {
        return result;
      }

      // There is no cached value yet, so, we need to actually create the class object
      synchronized (rcvr) {
        result = rcvr.readSlot(this);
        if (result != Nil.nilObject) {
          return result;
        }
        // ok, now it is for sure not initialized yet, instantiate class
        Object superclassAndMixins = mixinDefinition.getSuperclassAndMixinResolutionInvokable()
                                                    .createCallTarget().call(rcvr);
        SClass clazz = mixinDefinition.instantiateClass(rcvr, superclassAndMixins);
        rcvr.writeSlot(this, clazz);
        return clazz;
      }
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

  public Object[] getNestedMixinDefinitions() {
    return nestedMixinDefinitions.values().toArray(new Object[0]);
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
    MethodBuilder builder = new MethodBuilder(true, initializerBuilder.getLanguage(), null);
    builder.setSignature(init);
    builder.addArgument(Symbols.SELF,
        SomLanguage.getSyntheticSource("self read", "super-class-resolution")
                   .createSection(1));

    Source source = SomLanguage.getSyntheticSource("self", "Thing>>" + init.getString());
    SourceSection ss = source.createSection(0, 4);
    builder.setVarsOnMethodScope();
    builder.finalizeMethodScope();
    SInvokable thingInitNew = builder.assembleInitializer(
        builder.getSelfRead(ss), AccessModifier.PROTECTED, ss);
    instanceDispatchables.put(init, thingInitNew);
  }

  public Object instantiateObject(final Object... args) {
    VM.callerNeedsToBeOptimized("Only supposed to be used from Bootstrap.");
    assert args[0] instanceof SClass;
    SClass classObj = (SClass) args[0];
    SInvokable factory = (SInvokable) classObj.getSOMClass().lookupMessage(
        primaryFactoryName, AccessModifier.PUBLIC);
    return factory.invoke(args);
  }

  @Override
  public String toString() {
    return name.getString() + "[" + sourceSection + "]";
  }

  private MixinDefinition cloneForSplitting(final MixinScope adaptedInstanceScope,
      final MixinScope adaptedClassScope) {
    return new MixinDefinition(name, nameSection, primaryFactoryName,
        new ArrayList<ExpressionNode>(initializerBody), initializerBuilder, initializerSource,
        superclassMixinResolution,
        slots,
        new HashMap<SSymbol, Dispatchable>(instanceDispatchables),
        new HashMap<SSymbol, SInvokable>(factoryMethods),
        nestedMixinDefinitions, mixinId, accessModifier,
        adaptedInstanceScope, adaptedClassScope,
        allSlotsAreImmutable, outerScopeIsImmutable,
        isModule, sourceSection);
  }

  private SInvokable adaptInvokable(final SInvokable invokable, final MethodScope scope,
      final int appliesTo) {
    Method originalInvokable = (Method) invokable.getInvokable();
    MethodScope adaptedScope = originalInvokable.getLexicalScope().split(scope);
    Method adaptedInvokable = originalInvokable.cloneAndAdaptAfterScopeChange(
        adaptedScope, appliesTo + 1, false, true);
    return new SInvokable(invokable.getSignature(), invokable.getAccessModifier(),
        adaptedInvokable, invokable.getEmbeddedBlocks());
  }

  private void adaptFactoryMethods(final MethodScope scope, final int appliesTo) {
    for (SSymbol key : factoryMethods.keySet()) {
      SInvokable invokable = factoryMethods.get(key);
      SInvokable adaptedIvk = adaptInvokable(invokable, scope, appliesTo);
      factoryMethods.put(key, adaptedIvk);
    }
  }

  private void adaptInvokableDispatchables(final MethodScope scope, final int appliesTo) {
    for (SSymbol key : instanceDispatchables.keySet()) {
      Dispatchable dispatchable = instanceDispatchables.get(key);

      // Only need to adapt SInvokable, don't need to adapt slot definitions
      if (dispatchable instanceof SInvokable) {
        SInvokable invokable = (SInvokable) instanceDispatchables.get(key);
        SInvokable adaptedInvokable = adaptInvokable(invokable, scope, appliesTo);
        instanceDispatchables.put(key, adaptedInvokable);
      }
    }
  }

  public MixinDefinition cloneAndAdaptAfterScopeChange(final MethodScope adaptedScope,
      final int appliesTo) {
    MixinScope adaptedInstanceScope =
        new MixinScope(instanceScope.getOuterMixin(), adaptedScope);
    MixinScope adaptedClassScope = new MixinScope(classScope.getOuterMixin(), adaptedScope);

    MixinDefinition clone = cloneForSplitting(adaptedInstanceScope, adaptedClassScope);

    adaptedInstanceScope.setMixinDefinition(clone, false);
    adaptedClassScope.setMixinDefinition(clone, true);

    clone.adaptFactoryMethods(adaptedScope, appliesTo);
    clone.adaptInvokableDispatchables(adaptedScope, appliesTo);
    return clone;
  }
}
