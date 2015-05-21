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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import som.compiler.ClassDefinition.SlotDefinition;
import som.interpreter.LexicalScope.ClassScope;
import som.interpreter.SNodeFactory;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.primitives.NewObjectPrimFactory;
import som.vm.NotYetImplementedException;
import som.vm.Symbols;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SInvokable.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;

public final class ClassBuilder {

  private static final ClassBuilder moduleContextClassBuilder = new ClassBuilder(true);

  /** The method that is used to instantiate the class object. */
  private final MethodBuilder instantiation;

  /** The method that is used to initialize an instance. */
  private final MethodBuilder initializer;

  private final ArrayList<ExpressionNode> slotAndInitExprs = new ArrayList<>();

  private SSymbol                 name;
  private AbstractMessageSendNode superclassResolution;
  private final LinkedHashMap<SSymbol, SlotDefinition> slots = new LinkedHashMap<>();
  private final HashMap<SSymbol, SInvokable> methods = new HashMap<SSymbol, SInvokable>();
  private final HashMap<SSymbol, SInvokable> factoryMethods = new HashMap<SSymbol, SInvokable>();

  private final LinkedHashMap<SSymbol, ClassDefinition> embeddedClasses = new LinkedHashMap<>();

  private boolean classSide;

  private ExpressionNode superclassFactorySend;

  private final ClassScope   currentScope;
  private final ClassBuilder outerBuilder;

  private ClassBuilder(final boolean onlyForModules) {
    assert onlyForModules;

    initializer = null;
    instantiation = null;
    setName(Symbols.symbolFor("non-existing-module-context"));

    outerBuilder = null;
    currentScope = new ClassScope(null);
  }

  public ClassBuilder() {
    this(moduleContextClassBuilder);
  }

  public ClassBuilder(final ClassBuilder outerBuilder) {
    this.instantiation = createClassDefinitionContext();
    this.initializer   = new MethodBuilder(this);

    this.classSide = false;
    this.outerBuilder = outerBuilder;
    this.currentScope = new ClassScope(outerBuilder.getCurrentClassScope());
  }

  public static class ClassDefinitionError extends Exception {
    private static final long serialVersionUID = 9200967710874738189L;
    private final String message;
    private final SourceSection source;

    ClassDefinitionError(final String message, final SourceSection source) {
      this.message = message;
      this.source = source;
    }

    @Override
    public String toString() {
      return source.getSource().getName() + ":" + source.getStartLine() + ":" +
            source.getStartColumn() + ":error: " + message;
    }
  }

  public ClassScope getCurrentClassScope() {
    return currentScope;
  }

  public void setName(final SSymbol name) {
    assert this.name == null;
    this.name = name;
  }

  public SSymbol getName() {
    return name;
  }

  /**
   * Expression to resolve the super class at runtime, used in the instantiation.
   */
  public void setSuperClassResolution(final AbstractMessageSendNode superClass) {
    this.superclassResolution = superClass;
  }

  /**
   * The method that is used to instantiate the class object.
   * This method is based on the inheritance definition of the class.
   * Thus, it will resolve the super class to be used, and create the actual
   * runtime class object.
   */
  public MethodBuilder getInstantiationMethodBuilder() {
    return instantiation;
  }

  /**
   * The method that is used to initialize an instance.
   * It takes the arguments of the primary factory method, initializes the
   * slots, and executes the initializer expressions.
   */
  public MethodBuilder getInitializerMethodBuilder() {
    return initializer;
  }

  public void addMethod(final SInvokable meth) throws ClassDefinitionError {
    SSymbol name = meth.getSignature();
    if (!classSide) {
      if (slots.containsKey(name)) {
        throw new ClassDefinitionError("The class " + this.name.getString()
            + " already contains a slot named "
            + name.getString() + ". Can't define a method with the same name.",
            meth.getSourceSection());
      }
      if (methods.containsKey(name)) {
        throw new ClassDefinitionError("The class " + this.name.getString()
            + " already contains a method named "
            + name.getString() + ". Can't define another method with the same name.",
            meth.getSourceSection());
      }
      methods.put(name, meth);
    } else {
      factoryMethods.put(name, meth);
    }
  }

  public void addSlot(final SSymbol name, final AccessModifier acccessModifier,
      final boolean immutable, final ExpressionNode init,
      final SourceSection source) throws ClassDefinitionError {
    SlotDefinition slot = new SlotDefinition(name, acccessModifier,
        slots.size(), immutable, source);

    if (slots.containsKey(name)) {
      throw new ClassDefinitionError("The class " + this.name.getString() +
          " already defines a slot with the name '" + name.getString() + "'." +
          " A second slot with the same name is not possible.", source);
    }

    slots.put(name, slot);

    if (init != null) {
      slotAndInitExprs.add(SNodeFactory.createSlotInitialization(slot, init));
    }
  }

  public void addInitializerExpression(final ExpressionNode expression) {
    slotAndInitExprs.add(expression);
  }

  public boolean hasSlot(final SSymbol slot) {
    return slots.containsKey(slot);
  }

  public boolean isClassSide() {
    return classSide;
  }

  public void switchToClassSide() {
    classSide = true;
  }

  public ClassDefinition assemble(final SourceSection source) {
    // to prepare the class definition we need to assemble:
    //   - the class instantiation method, which resolves super
    //   - the primary factory method, which allocates the object,
    //     and then calls initiation
    //   - the initialization method, which class super, and then initializes the object

    SMethod classObjectInstantiation = assembleClassObjectInstantiationMethod();
    SMethod primaryFactory = assemblePrimaryFactoryMethod();
    SMethod initializationMethod = assembleInitializationMethod();
    factoryMethods.put(primaryFactory.getSignature(), primaryFactory);
    methods.put(initializationMethod.getSignature(), initializationMethod);

    ClassDefinition clsDef = new ClassDefinition(name, classObjectInstantiation,
        methods, factoryMethods, embeddedClasses, slots, source);
    currentScope.setClassDefinition(clsDef);

    return clsDef;
  }

  @TruffleBoundary
  public void assembleSystemClass(final SClass systemClass) {
    throw new NotYetImplementedException();
//    systemClass.setInstanceInvokables(
//        SArray.create(methods.toArray(new Object[0])));
//    systemClass.setInstanceFields(
//        SArray.create(slots.toArray(new Object[0])));
//    // class-bound == class-instance-bound
//    SClass superMClass = systemClass.getSOMClass();
//    superMClass.setInstanceInvokables(
//        SArray.create(factoryMethods.toArray(new Object[0])));
  }

  private static MethodBuilder createClassDefinitionContext() {
    MethodBuilder definitionMethod = new MethodBuilder(universeClassBuilder);
    // self is going to be either universe, or the enclosing object
    definitionMethod.addArgumentIfAbsent("self");
    definitionMethod.setSignature(Symbols.symbolFor("`define`cls"));

    definitionMethod.addLocalIfAbsent("$superCls");
    return definitionMethod;
  }

  private SMethod assembleClassObjectInstantiationMethod() {
    assert superclassResolution != null;
    ExpressionNode body = SNodeFactory.createConstructClassNode(superclassResolution);
    return instantiation.assemble(body,
        AccessModifier.OBJECT_INSTANTIATION_METHOD, null, null);
  }

  private SMethod assemblePrimaryFactoryMethod() {
    MethodBuilder builder = new MethodBuilder(this);
    builder.setSignature(initializer.getSignature());
    builder.addArgumentIfAbsent("self");

    // first create new Object
    ExpressionNode newObject = NewObjectPrimFactory.create(
        builder.getReadNode("self", null));

    List<ExpressionNode> args = createFactoryMethodArgumentRead(builder,
        newObject);

    // This is a bet on initializer methods being constructed well,
    // so that they return self
    ExpressionNode initializedObject = SNodeFactory.createMessageSend(
        getInitializerName(initializer.getSignature()), args, null);

    return builder.assemble(initializedObject, AccessModifier.PROTECTED,
        Symbols.symbolFor("initialization"), null);
  }

  private SMethod assembleInitializationMethod() {
    List<ExpressionNode> allExprs = new ArrayList<ExpressionNode>(1 + slotAndInitExprs.size());
    // first do initializer send to super class
    allExprs.add(superclassFactorySend);

    // then, evaluate the slot and init expressions
    allExprs.addAll(slotAndInitExprs);
    ExpressionNode body = SNodeFactory.createSequence(allExprs, null);
    return initializer.assemble(body, AccessModifier.PROTECTED,
        Symbols.symbolFor("initialization"), null);
  }

  protected List<ExpressionNode> createFactoryMethodArgumentRead(
      final MethodBuilder builder, final ExpressionNode receiver) {
    // then, call the initializer on it
    List<ExpressionNode> args = new ArrayList<>(
        initializer.getSignature().getNumberOfSignatureArguments());
    args.add(receiver);

    String[] arguments = initializer.getArgumentNames();

    for (String arg : arguments) {
      if (!"self".equals(arg)) { // we already got self
        builder.addArgumentIfAbsent(arg.toString());
        args.add(builder.getReadNode(arg, null));
      }
    }
    return args;
  }

  public ExpressionNode createStandardSuperFactorySend() {
    ExpressionNode superNode = initializer.getSuperReadNode(null);
    ExpressionNode superFactorySend = SNodeFactory.createMessageSend(
        getInitializerName(symbolFor("new")),
        new ExpressionNode[] {superNode}, null);
    return superFactorySend;
  }

  public SSymbol getInitializerName(final SSymbol selector) {
    return Symbols.symbolFor("initializer`" + selector.getString());
  }

  @Override
  public String toString() {
    return "ClassGenC(" + name.getString() + ")";
  }

  public void setSuperclassFactorySend(final ExpressionNode superFactorySend) {
    this.superclassFactorySend = superFactorySend;
  }

  public void addNestedClass(final ClassDefinition nestedClass) {
    embeddedClasses.put(nestedClass.getName(), nestedClass);
  }
}
