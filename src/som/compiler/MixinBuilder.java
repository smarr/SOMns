/**
 * Copyright (c) 2013 Stefan Marr,   stefan.marr@vub.ac.be
 * Copyright (c) 2009 Michael Haupt, michael.haupt@hpi.uni-potsdam.de
 * Software Architecture Group, Hasso Plattner Institute, Potsdam, Germany
 * http://www.hpi.uni-potsdam.de/swa/
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package som.compiler;

import static som.vm.Symbols.symbolFor;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.source.SourceSection;

import bd.tools.structure.StructuralProbe;
import som.compiler.MixinDefinition.ClassSlotDefinition;
import som.compiler.MixinDefinition.SlotDefinition;
import som.compiler.MixinDefinition.SlotMutator;
import som.compiler.Variable.Argument;
import som.compiler.Variable.Internal;
import som.compiler.Variable.Local;
import som.interpreter.LexicalScope;
import som.interpreter.LexicalScope.MixinScope;
import som.interpreter.Method;
import som.interpreter.SNodeFactory;
import som.interpreter.SomLanguage;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.IsValueCheckNode;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.objectstorage.InitializerFieldWrite;
import som.primitives.NewObjectPrimNodeGen;
import som.vm.Symbols;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;


/**
 * MixinBuilders are used by the parser to accumulate all information to create
 * a {@link MixinDefinition}.
 */
public final class MixinBuilder extends ScopeBuilder<MixinScope> {
  // TODO: if performance critical, optimize mixin builder by initializing structures lazily

  /** The method that is used to resolve the superclass at runtime. */
  private final MethodBuilder superclassAndMixinResolutionBuilder;
  private ExpressionNode      superclassResolution;

  private final ArrayList<ExpressionNode> mixinResolvers = new ArrayList<>();
  private SourceSection                   mixinResolversSource;

  /** The method that is used to initialize an instance. */
  private final MethodBuilder initializer;

  /** The method that is used for instantiating the object. */
  private final MethodBuilder primaryFactoryMethod;

  private SourceSection primaryFactorySource;
  private SourceSection initializerSource;

  private final ArrayList<ExpressionNode> slotAndInitExprs = new ArrayList<>();

  private final SSymbol       name;
  private final SourceSection nameSection;

  @SuppressWarnings("unused") private String mixinComment;

  private final EconomicMap<SSymbol, SlotDefinition> slots         = EconomicMap.create();
  private final EconomicMap<SSymbol, Dispatchable>   dispatchables = EconomicMap.create();

  private final EconomicMap<SSymbol, SInvokable> factoryMethods = EconomicMap.create();

  private boolean allSlotsAreImmutable = true;

  private final EconomicMap<SSymbol, MixinDefinition> embeddedMixins = EconomicMap.create();

  private boolean classSide;

  private ExpressionNode superclassFactorySend;
  private boolean        isSimpleNewSuperFactoySend;

  private final ArrayList<ExpressionNode> mixinFactorySends = new ArrayList<>();

  private final AccessModifier accessModifier;

  private final MixinScope classScope;

  private final MixinDefinitionId mixinId;

  private final StructuralProbe<SSymbol, MixinDefinition, SInvokable, SlotDefinition, Variable> structuralProbe;

  private final SomLanguage language;

  /**
   * A unique id to identify the mixin definition. Having the Id distinct from
   * the actual definition allows us to make the definition immutable and
   * construct it only after the parsing is completed.
   * Currently, this is necessary because we want the immutability, and at the
   * same time need a way to identify a mixin later on in super sends.
   *
   * Since the class object initialization method needs to support super,
   * it is not really possible to do it differently at the moment.
   */
  public static final class MixinDefinitionId {
    private final SSymbol name;

    public MixinDefinitionId(final SSymbol name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return "MixinDefId(" + name + ")";
    }
  };

  public MixinBuilder(final ScopeBuilder<?> outer, final AccessModifier accessModifier,
      final SSymbol name, final SourceSection nameSection,
      final StructuralProbe<SSymbol, MixinDefinition, SInvokable, SlotDefinition, Variable> structuralProbe,
      final SomLanguage language) {
    super(outer, outer == null ? null : outer.getScope());
    this.name = name;
    this.nameSection = nameSection;
    this.mixinId = new MixinDefinitionId(name);

    this.classSide = false;
    this.language = language;

    this.classScope = createScope(scope);

    this.initializer = new MethodBuilder(this, structuralProbe);
    this.primaryFactoryMethod =
        new MethodBuilder(this, classScope, false, language, structuralProbe);
    this.superclassAndMixinResolutionBuilder = createSuperclassResolutionBuilder();

    this.accessModifier = accessModifier;
    this.structuralProbe = structuralProbe;
  }

  public static class MixinDefinitionError extends SemanticDefinitionError {
    private static final long serialVersionUID = 5030639383869198851L;

    MixinDefinitionError(final String message, final SourceSection source) {
      super(message, source);
    }
  }

  @Override
  protected MixinScope createScope(final LexicalScope outer) {
    return new MixinScope(outer);
  }

  @Override
  public MixinBuilder getMixin() {
    return this;
  }

  @Override
  public MethodBuilder getMethod() {
    return null;
  }

  public SomLanguage getLanguage() {
    return language;
  }

  public boolean isLiteral() {
    return outer instanceof MethodBuilder;
  }

  @Override
  public String getName() {
    return name.getString();
  }

  public boolean isModule() {
    return outer == null;
  }

  public AccessModifier getAccessModifier() {
    return accessModifier;
  }

  @Override
  protected int getContextLevel(final SSymbol varName) {
    assert outer != null : "If there is no outer context, "
        + "something is wrong with lexcial scoping. Could not find var: "
        + varName.getString();
    return outer.getContextLevel(varName);
  }

  @Override
  protected Local getLocal(final SSymbol varName) {
    if (outer == null) {
      return null;
    }
    return outer.getLocal(varName);
  }

  @Override
  protected Variable getVariable(final SSymbol varName) {
    if (outer == null) {
      return null;
    }
    return outer.getVariable(varName);
  }

  @Override
  protected boolean hasArgument(final SSymbol varName) {
    if (outer == null) {
      return false;
    }
    return outer.hasArgument(varName);
  }

  @Override
  public Internal getFrameOnStackMarkerVar() {
    // null, because we use this for non-local returns,
    // which are returning from methods
    // so, with this method, we just look for the closest enclosing object method
    return null;
  }

  /**
   * Expression to resolve the super class at runtime, used in the instantiation.
   */
  public void setSuperClassResolution(final ExpressionNode superClass) {
    superclassResolution = superClass;
  }

  public void addMixinResolver(final ExpressionNode mixin) {
    mixinResolvers.add(mixin);
  }

  public void setMixinResolverSource(final SourceSection mixin) {
    mixinResolversSource = mixin;
  }

  public ExpressionNode constructSuperClassResolution(final SSymbol superClass,
      final SourceSection source) {
    MethodBuilder def = getClassInstantiationMethodBuilder();
    ExpressionNode selfRead = def.getSelfRead(source);
    return SNodeFactory.createMessageSend(superClass,
        new ExpressionNode[] {selfRead}, false, source, null, language);
  }

  /**
   * The method that is used to instantiate the class object.
   * This method is based on the inheritance definition of the class.
   * Thus, it will resolve the super class to be used, and create the actual
   * runtime class object.
   */
  public MethodBuilder getClassInstantiationMethodBuilder() {
    return superclassAndMixinResolutionBuilder;
  }

  /**
   * The method that is used to initialize an instance.
   * It takes the arguments of the primary factory method, initializes the
   * slots, and executes the initializer expressions.
   */
  public MethodBuilder getInitializerMethodBuilder() {
    return initializer;
  }

  public void finalizeInitializer() {
    initializer.finalizeMethodScope();
  }

  /**
   * The method that is used to instantiate an object.
   * It instantiates the object, and then calls the initializer,
   * passing all arguments.
   */
  public MethodBuilder getPrimaryFactoryMethodBuilder() {
    return primaryFactoryMethod;
  }

  /**
   * Primary factor and initializer take the same arguments, and
   * the initializers name is derived from the factory method.
   */
  public void setupInitializerBasedOnPrimaryFactory(final SourceSection sourceSection) {
    primaryFactorySource = sourceSection;

    initializer.setSignature(getInitializerName(
        primaryFactoryMethod.getSignature()));
    for (Argument arg : primaryFactoryMethod.getArguments()) {
      initializer.addArgument(arg.name, arg.source);
    }
    initializer.setVarsOnMethodScope();
  }

  public void setInitializerSource(final SourceSection sourceSection) {
    initializerSource = sourceSection;
  }

  public void addMethod(final SInvokable meth) throws MixinDefinitionError {
    SSymbol name = meth.getSignature();
    if (!classSide) {
      Dispatchable existing = dispatchables.get(name);
      if (existing != null) {
        throw new MixinDefinitionError("The class " + this.name.getString()
            + " already contains a " + existing.typeForErrors() + " named "
            + name.getString() + ". Can't define a method with the same name.",
            meth.getSourceSection());
      }
      dispatchables.put(name, meth);
    } else {
      addFactoryMethod(meth, name, false);
    }
  }

  /**
   * Add a pre-determined set of methods to the mixin.
   * This is currently used for constructing the class
   * that contains primitive methods of extension modules.
   */
  public void addMethods(final EconomicMap<SSymbol, Dispatchable> disps) {
    dispatchables.putAll(disps);
  }

  private void addFactoryMethod(final SInvokable meth, final SSymbol name,
      final boolean isPrimary) throws MixinDefinitionError {
    SInvokable existing = factoryMethods.get(name);
    if (!isPrimary) {
      if (existing != null) {
        throw new MixinDefinitionError("The class " + this.name.getString()
            + " already contains a " + existing.typeForErrors() + " named "
            + name.getString() + ". Can't define a method with the same name.",
            meth.getSourceSection());
      }
      factoryMethods.put(name, meth);
      return;
    }
    // we allow overriding the primary factory method here for convenient
    // hacks, example: ValueArray uses this to delegate to the primitive.
    // Note, this code reads a bit backwards because the primary factory is
    // added here only after all other factory methods.
    // Further, there is an expectation that all methods in the system get set
    // their holders, so, we're going to mess a little with the ones
    // that are overridden to keep them in the dict
    if (existing != null) {
      // We use here the SSymbol constructor directly to not record the symbol in the
      // global table, which also neatly guarantees uniqueness with same string
      SSymbol hackedName = new SSymbol("\0!" + name.getString());
      assert !factoryMethods.containsKey(hackedName);
      factoryMethods.put(hackedName, meth);
    } else {
      factoryMethods.put(name, meth);
    }
  }

  public void addSlot(final SSymbol name, final AccessModifier acccessModifier,
      final boolean immutable, final ExpressionNode init,
      final SourceSection source) throws MixinDefinitionError {
    if (dispatchables.containsKey(name)) {
      throw new MixinDefinitionError("The class " + this.name.getString() +
          " already defines a slot with the name '" + name.getString() + "'." +
          " A second slot with the same name is not possible.", source);
    }

    SlotDefinition slot = new SlotDefinition(name, acccessModifier, immutable,
        source);
    slots.put(name, slot);

    if (!immutable) {
      allSlotsAreImmutable = false;
    }

    dispatchables.put(name, slot);
    if (!immutable) {
      dispatchables.put(getSetterName(name),
          new SlotMutator(name, acccessModifier, immutable, source, slot));
    }

    if (init != null) {
      ExpressionNode self = initializer.getSelfRead(source);
      InitializerFieldWrite write = slot.getInitializerWriteNode(self, init, source);
      write.markAsStatement();
      slotAndInitExprs.add(write);
    }

    if (structuralProbe != null) {
      structuralProbe.recordNewSlot(slot);
    }
  }

  /**
   * Determining whether this class defines a slot with the given name
   * Currently used only for determining the context of variables whose
   * activation's enclose an object literals,
   * see {@link MethodBuilder#getContextLevel}.
   */
  public boolean hasSlotDefined(final SSymbol name) {
    return slots.containsKey(name);
  }

  public void addInitializerExpression(final ExpressionNode expression) {
    expression.markAsStatement();
    slotAndInitExprs.add(expression);
  }

  public boolean isClassSide() {
    return classSide;
  }

  public MixinScope getScopeForCurrentParserPosition() {
    if (classSide) {
      return classScope;
    } else {
      return scope;
    }
  }

  public void switchToClassSide() {
    classSide = true;
  }

  public MixinDefinition assemble(final SourceSection source) {
    // to prepare the mixin definition we need to assemble:
    // - the class instantiation method, which resolves super
    // - the primary factory method, which allocates the object,
    // and then calls initiation
    // - the initialization method, which class super, and then initializes the object

    Method superclassResolution = assembleSuperclassAndMixinResoltionMethod();
    SInvokable primaryFactory = assemblePrimaryFactoryMethod();
    SInvokable initializationMethod = assembleInitializationMethod();
    try {
      addFactoryMethod(primaryFactory, primaryFactory.getSignature(), true);
    } catch (MixinDefinitionError e) {
      throw new RuntimeException(e); // This should never happen
    }

    if (initializationMethod != null) {
      dispatchables.put(
          initializationMethod.getSignature(), initializationMethod);
    }

    MixinDefinition clsDef = new MixinDefinition(name, nameSection,
        primaryFactory.getSignature(), slotAndInitExprs, initializer,
        initializerSource, superclassResolution,
        slots, dispatchables, factoryMethods, embeddedMixins, mixinId,
        accessModifier, scope, classScope, allSlotsAreImmutable,
        isModule() || outer.isImmutable(), isModule(), source);
    scope.setMixinDefinition(clsDef, false);
    classScope.setMixinDefinition(clsDef, true);

    setHolders(clsDef);

    if (structuralProbe != null) {
      structuralProbe.recordNewClass(clsDef);
    }
    return clsDef;
  }

  @Override
  protected boolean isImmutable() {
    if (!allSlotsAreImmutable) {
      return false;
    }

    if (outer != null) {
      return outer.isImmutable();
    }

    return true;
  }

  private void setHolders(final MixinDefinition clsDef) {
    assert clsDef != null;
    for (Dispatchable disp : dispatchables.getValues()) {
      if (disp instanceof SInvokable) {
        ((SInvokable) disp).setHolder(clsDef);
      }
    }

    for (SInvokable invok : factoryMethods.getValues()) {
      invok.setHolder(clsDef);
    }
  }

  private MethodBuilder createSuperclassResolutionBuilder() {
    MethodBuilder definitionMethod;
    if (isModule()) {
      definitionMethod = new MethodBuilder(true, language, structuralProbe);
    } else {
      definitionMethod =
          new MethodBuilder(outer, outer.scope, false, language, structuralProbe);
    }

    // self is going to be the enclosing object
    definitionMethod.addArgument(Symbols.SELF,
        SomLanguage.getSyntheticSource("self read", "super-class-resolution")
                   .createSection(1));
    definitionMethod.setSignature(Symbols.DEF_CLASS);

    return definitionMethod;
  }

  private Method assembleSuperclassAndMixinResoltionMethod() {
    ExpressionNode resolution;
    SourceSection source;
    if (mixinResolvers.isEmpty()) {
      resolution = superclassResolution;
      source = superclassResolution.getSourceSection();
    } else {
      ExpressionNode[] exprs = new ExpressionNode[mixinResolvers.size() + 1];
      exprs[0] = superclassResolution;
      for (int i = 0; i < mixinResolvers.size(); i++) {
        exprs[i + 1] = mixinResolvers.get(i);
      }

      resolution = SNodeFactory.createInternalObjectArray(exprs, mixinResolversSource);
      source = mixinResolversSource;
    }

    superclassAndMixinResolutionBuilder.setVarsOnMethodScope();
    superclassAndMixinResolutionBuilder.finalizeMethodScope();

    assert superclassResolution != null;
    return superclassAndMixinResolutionBuilder.assembleInvokable(resolution,
        source);
  }

  private SInvokable assemblePrimaryFactoryMethod() {
    // first create new Object

    ExpressionNode newObject = NewObjectPrimNodeGen.create(mixinId,
        primaryFactoryMethod.getSelfRead(primaryFactorySource));
    newObject.initialize(primaryFactorySource);

    List<ExpressionNode> args = createPrimaryFactoryArgumentRead(newObject);

    // This is a bet on initializer methods being constructed well,
    // so that they return self
    ExpressionNode initializedObject = SNodeFactory.createMessageSend(
        initializer.getSignature(), args, primaryFactorySource, language.getVM());

    primaryFactoryMethod.setVarsOnMethodScope();
    primaryFactoryMethod.finalizeMethodScope();
    return primaryFactoryMethod.assemble(initializedObject,
        AccessModifier.PUBLIC, primaryFactorySource);
  }

  private static final SSymbol StandardInitializer =
      MixinBuilder.getInitializerName(Symbols.NEW);

  private SInvokable assembleInitializationMethod() {
    if (isSimpleNewSuperFactoySend
        && slotAndInitExprs.size() == 0
        && initializer.getSignature() == StandardInitializer
        && mixinFactorySends.size() == 0) {
      return null; // this is strictly an optimization, should work without it!
    }

    List<ExpressionNode> allExprs = new ArrayList<ExpressionNode>(1 + slotAndInitExprs.size());
    // first, do initializer send to super class
    allExprs.add(superclassFactorySend);

    // second, do initializer sends for mixins
    allExprs.addAll(mixinFactorySends);

    // then, evaluate the slot and init expressions
    allExprs.addAll(slotAndInitExprs);

    if (mixinFactorySends.size() > 0 || slotAndInitExprs.size() > 0) {
      // we need to make sure that we return self, that's the SOM Newspeak
      // contract for initializers
      // and we need to make sure that a potential Value class verifies
      // that it actually is a value
      allExprs.add(IsValueCheckNode.create(
          initializerSource, initializer.getSelfRead(initializerSource)));
    }

    ExpressionNode body = SNodeFactory.createSequence(allExprs, initializerSource);
    return initializer.assembleInitializer(body, AccessModifier.PROTECTED,
        initializerSource);
  }

  protected List<ExpressionNode> createPrimaryFactoryArgumentRead(
      final ExpressionNode objectInstantiationExpr) {
    // then, call the initializer on it
    Iterable<Argument> arguments = primaryFactoryMethod.getArguments();
    List<ExpressionNode> args = new ArrayList<>();
    args.add(objectInstantiationExpr);

    for (Argument arg : arguments) {
      if (Symbols.SELF != arg.name) { // already have self as the newly instantiated object
        args.add(primaryFactoryMethod.getReadNode(arg.name, arg.source));
      }
    }
    return args;
  }

  public ExpressionNode createStandardSuperFactorySend(final SourceSection source) {
    ExpressionNode superNode = initializer.getSuperReadNode(source);
    SSymbol init = getInitializerName(Symbols.NEW);
    ExpressionNode superFactorySend = SNodeFactory.createMessageSend(
        init, new ExpressionNode[] {superNode}, false, source, null, language);
    return superFactorySend;
  }

  public static SSymbol getSetterName(final SSymbol selector) {
    assert !selector.getString().endsWith(":");
    return symbolFor(selector.getString() + ":");
  }

  public static SSymbol getInitializerName(final SSymbol selector) {
    return symbolFor("initializer`" + selector.getString());
  }

  public static SSymbol getInitializerName(final SSymbol selector,
      final int mixinId) {
    return symbolFor("initializer`" + mixinId + "`" + selector.getString());
  }

  @Override
  public String toString() {
    String n = name != null ? name.getString() : "";
    return "MixinBuilder(" + n + ")";
  }

  public void addMixinFactorySend(final ExpressionNode mixinFactorySend) {
    mixinFactorySends.add(mixinFactorySend);
  }

  public void setSuperclassFactorySend(final ExpressionNode superFactorySend,
      final boolean isSimpleNewSuperFactoySend) {
    this.superclassFactorySend = superFactorySend;
    this.isSimpleNewSuperFactoySend = isSimpleNewSuperFactoySend;
  }

  public void addNestedMixin(final MixinDefinition nestedMixin)
      throws MixinDefinitionError {
    SSymbol name = nestedMixin.getName();
    Dispatchable disp = dispatchables.get(name);
    if (disp != null) {
      throw new MixinDefinitionError("The class " + this.name.getString() +
          " already defines a " + disp.typeForErrors() + " with the name '" +
          name.getString() + "'." +
          " Defining an inner class with the same name is not possible.",
          nestedMixin.getSourceSection());
    }

    embeddedMixins.put(name, nestedMixin);
    ClassSlotDefinition cacheSlot = new ClassSlotDefinition(name, nestedMixin);
    dispatchables.put(name, cacheSlot);
    slots.put(name, cacheSlot);
  }

  public MixinDefinitionId getMixinId() {
    return mixinId;
  }

  public void setComment(final String comment) {
    mixinComment = comment;
  }
}
