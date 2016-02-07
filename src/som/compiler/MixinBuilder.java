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

import static som.compiler.Tags.NEW_OBJECT;
import static som.vm.Symbols.symbolFor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import som.compiler.MixinDefinition.ClassSlotDefinition;
import som.compiler.MixinDefinition.SlotDefinition;
import som.compiler.MixinDefinition.SlotMutator;
import som.compiler.Variable.Argument;
import som.interpreter.LexicalScope.MixinScope;
import som.interpreter.Method;
import som.interpreter.SNodeFactory;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.IsValueCheckNode;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.primitives.NewObjectPrimNodeGen;
import som.vm.Symbols;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;


/**
 * MixinBuilders are used by the parser to accumulate all information to create
 * a {@link MixinDefinition}.
 */
public final class MixinBuilder {
  // TODO: if performance critical, optimize mixin builder by initializing structures lazily

  /** The method that is used to resolve the superclass at runtime. */
  private final MethodBuilder superclassAndMixinResolutionBuilder;
  private ExpressionNode superclassResolution;
  private final ArrayList<ExpressionNode> mixinResolvers = new ArrayList<>();
  private SourceSection mixinResolversSource;

  /** The method that is used to initialize an instance. */
  private final MethodBuilder initializer;

  /** The method that is used for instantiating the object. */
  private final MethodBuilder primaryFactoryMethod;

  private SourceSection primaryFactorySource;
  private SourceSection initializerSource;

  private final ArrayList<ExpressionNode> slotAndInitExprs = new ArrayList<>();

  private final SSymbol name;
  private String mixinComment;

  private final HashMap<SSymbol, SlotDefinition> slots = new HashMap<>();
  private final LinkedHashMap<SSymbol, Dispatchable> dispatchables = new LinkedHashMap<>();
  private final HashMap<SSymbol, SInvokable> factoryMethods = new HashMap<SSymbol, SInvokable>();
  private boolean allSlotsAreImmutable = true;

  private final LinkedHashMap<SSymbol, MixinDefinition> embeddedMixins = new LinkedHashMap<>();

  private boolean classSide;

  private ExpressionNode superclassFactorySend;
  private boolean   isSimpleNewSuperFactoySend;
  private final ArrayList<ExpressionNode> mixinFactorySends = new ArrayList<>();

  private final AccessModifier accessModifier;

  private final MixinScope instanceScope;
  private final MixinScope classScope;

  private final MixinBuilder outerBuilder;

  private final MixinDefinitionId mixinId;

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

  public MixinBuilder(final MixinBuilder outerBuilder,
      final AccessModifier accessModifier, final SSymbol name) {
    this.name         = name;
    this.mixinId      = new MixinDefinitionId(name);

    this.classSide    = false;
    this.outerBuilder = outerBuilder;

    // classes can only be defined on the instance side,
    // so, both time the instance scope
    this.instanceScope = new MixinScope(outerBuilder != null ? outerBuilder.getInstanceScope() : null);
    this.classScope    = new MixinScope(outerBuilder != null ? outerBuilder.getInstanceScope() : null);

    this.initializer          = new MethodBuilder(this, this.instanceScope);
    this.primaryFactoryMethod = new MethodBuilder(this, this.classScope);
    this.superclassAndMixinResolutionBuilder = createSuperclassResolutionBuilder();

    this.accessModifier = accessModifier;
  }

  public static class MixinDefinitionError extends Exception {
    private static final long serialVersionUID = 9200967710874738189L;
    private final String message;
    private final SourceSection source;

    MixinDefinitionError(final String message, final SourceSection source) {
      this.message = message;
      this.source = source;
    }

    @Override
    public String toString() {
      return source.getSource().getName() + ":" + source.getStartLine() + ":" +
            source.getStartColumn() + ":error: " + message;
    }
  }

  public MixinScope getInstanceScope() {
    return instanceScope;
  }

  public MixinBuilder getOuterBuilder() {
    return outerBuilder;
  }

  public SSymbol getName() {
    return name;
  }

  public boolean isModule() {
    return outerBuilder == null;
  }

  public AccessModifier getAccessModifier() {
    return accessModifier;
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
      initializer.addArgumentIfAbsent(arg.name, arg.source);
    }
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
      slotAndInitExprs.add(slot.getInitializerWriteNode(self, init, source));
    }
  }

  public void addInitializerExpression(final ExpressionNode expression) {
    slotAndInitExprs.add(expression);
  }

  public boolean isClassSide() {
    return classSide;
  }

  public MixinScope getScopeForCurrentParserPosition() {
    if (classSide) {
      return classScope;
    } else {
      return instanceScope;
    }
  }

  public void switchToClassSide() {
    classSide = true;
  }

  public MixinDefinition assemble(final SourceSection source) {
    // to prepare the mixin definition we need to assemble:
    //   - the class instantiation method, which resolves super
    //   - the primary factory method, which allocates the object,
    //     and then calls initiation
    //   - the initialization method, which class super, and then initializes the object

    Method superclassResolution = assembleSuperclassAndMixinResoltionMethod();
    SInvokable primaryFactory       = assemblePrimaryFactoryMethod();
    SInvokable initializationMethod = assembleInitializationMethod();
    factoryMethods.put(primaryFactory.getSignature(), primaryFactory);

    if (initializationMethod != null) {
      dispatchables.put(
          initializationMethod.getSignature(), initializationMethod);
    }

    MixinDefinition clsDef = new MixinDefinition(name,
        primaryFactory.getSignature(), slotAndInitExprs, initializer,
        initializerSource, superclassResolution,
        slots, dispatchables, factoryMethods, embeddedMixins, mixinId,
        accessModifier, instanceScope, classScope, allSlotsAreImmutable,
        outerScopeIsImmutable(), isModule(), source);
    instanceScope.setMixinDefinition(clsDef, false);
    classScope.setMixinDefinition(clsDef, true);

    setHolders(clsDef);
    return clsDef;
  }

  private boolean outerScopeIsImmutable() {
    if (outerBuilder == null) {
      return true;
    }
    return outerBuilder.allSlotsAreImmutable && outerBuilder.outerScopeIsImmutable();
  }

  private void setHolders(final MixinDefinition clsDef) {
    for (Dispatchable disp : dispatchables.values()) {
      if (disp instanceof SInvokable) {
        ((SInvokable) disp).setHolder(clsDef);
      }
    }

    for (SInvokable invok : factoryMethods.values()) {
      invok.setHolder(clsDef);
    }
  }

  private MethodBuilder createSuperclassResolutionBuilder() {
    MethodBuilder definitionMethod;
    if (outerBuilder == null) {
      definitionMethod = new MethodBuilder(true);
    } else {
      definitionMethod = new MethodBuilder(outerBuilder,
          outerBuilder.getInstanceScope());
    }
    // self is going to be the enclosing object
    definitionMethod.addArgumentIfAbsent("self",
        Source.fromNamedText("self read", "super-class-resolution").
        createSection("self read", 1).cloneWithTags(Tags.SYNTAX_ARGUMENT));
    definitionMethod.setSignature(Symbols.DEF_CLASS);

    return definitionMethod;
  }

  private Method assembleSuperclassAndMixinResoltionMethod() {
    ExpressionNode resolution;
    SourceSection  source;
    if (mixinResolvers.isEmpty()) {
      resolution = superclassResolution;
      source = superclassResolution.getSourceSection();
    } else {
      ExpressionNode[] exprs = new ExpressionNode[mixinResolvers.size() + 1];
      exprs[0] = superclassResolution;
      for (int i = 0; i < mixinResolvers.size(); i++) {
        exprs[i + 1] = mixinResolvers.get(i);
      }

      resolution = SNodeFactory.createInternalObjectArray(exprs);
      source = mixinResolversSource;
    }

    assert superclassResolution != null;
    return superclassAndMixinResolutionBuilder.assembleInvokable(resolution,
        source);
  }

  private SInvokable assemblePrimaryFactoryMethod() {
    // first create new Object

    ExpressionNode newObject = NewObjectPrimNodeGen.create(
        primaryFactorySource.cloneWithTags(NEW_OBJECT), mixinId,
        primaryFactoryMethod.getSelfRead(null));

    List<ExpressionNode> args = createPrimaryFactoryArgumentRead(newObject);

    // This is a bet on initializer methods being constructed well,
    // so that they return self
    ExpressionNode initializedObject = SNodeFactory.createMessageSend(
        initializer.getSignature(), args, primaryFactorySource);

    return primaryFactoryMethod.assemble(initializedObject,
        AccessModifier.PUBLIC, Symbols.INITIALIZATION,
        primaryFactorySource);
  }

  private static final SSymbol StandardInitializer = MixinBuilder.getInitializerName(Symbols.NEW);

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
      allExprs.add(IsValueCheckNode.create(initializer.getSelfRead(null)));
    }

    ExpressionNode body = SNodeFactory.createSequence(allExprs, null);
    return initializer.assembleInitializer(body, AccessModifier.PROTECTED,
        Symbols.INITIALIZATION, initializerSource);
  }

  protected List<ExpressionNode> createPrimaryFactoryArgumentRead(
      final ExpressionNode objectInstantiationExpr) {
    // then, call the initializer on it
    Collection<Argument> arguments = primaryFactoryMethod.getArguments();
    List<ExpressionNode> args = new ArrayList<>(arguments.size());
    args.add(objectInstantiationExpr);

    for (Argument arg : arguments) {
      if (!"self".equals(arg.name)) { // already have self as the newly instantiated object
        args.add(primaryFactoryMethod.getReadNode(arg.name, arg.source));
      }
    }
    return args;
  }

  public ExpressionNode createStandardSuperFactorySend(final SourceSection source) {
    ExpressionNode superNode = initializer.getSuperReadNode(null);
    SSymbol init = getInitializerName(Symbols.NEW);
    ExpressionNode superFactorySend = SNodeFactory.createMessageSend(
        init, new ExpressionNode[] {superNode}, false, source);
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
