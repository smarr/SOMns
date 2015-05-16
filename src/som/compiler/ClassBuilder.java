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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import som.compiler.ClassDefinition.SlotDefinition;
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

public final class ClassBuilder {

  private static final ClassBuilder universeClassBuilder = new ClassBuilder(true);

  private ClassBuilder(final boolean onlyForUnivserClass) {
    assert onlyForUnivserClass;

    initializer = null;
    instantiation = null;
    setName(Symbols.symbolFor("Universe"));
  }

  public ClassBuilder() {
    this.instantiation = createClassDefinitionContext();
    this.initializer   = new MethodBuilder(this);

    this.classSide = false;
  }

  private final MethodBuilder instantiation;
  private final MethodBuilder initializer;

  private final ArrayList<ExpressionNode> slotAndInitExprs = new ArrayList<>();

  private SSymbol             name;
  private AbstractMessageSendNode superclassResolution;
  private final LinkedHashMap<SSymbol, SlotDefinition> slots = new LinkedHashMap<>();
  private final List<SInvokable> methods = new ArrayList<SInvokable>();
  private final List<SInvokable> factoryMethods  = new ArrayList<SInvokable>();

  private boolean classSide;

  private SSymbol primaryFactoryMethodName;
  private List<String> primaryFactoryArguments;
  private SSymbol primaryFactoryMethodNameOfSuperClass;

  public void setName(final SSymbol name) {
    assert this.name == null;
    this.name = name;
  }

  public SSymbol getName() {
    return name;
  }

  public void setPrimaryFactoryMethodName(final SSymbol name) {
    primaryFactoryMethodName = name;
  }

  public void setPrimaryFactoryArguments(final List<String> arguments) {
    primaryFactoryArguments = arguments;
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

  public void addMethod(final SInvokable meth) {
    if (!classSide) {
      methods.add(meth);
    } else {
      factoryMethods.add(meth);
    }
  }

  public void addSlot(final SSymbol name, final AccessModifier acccessModifier,
      final boolean immutable, final ExpressionNode init) {
    SlotDefinition slot = new SlotDefinition(name, acccessModifier, immutable);
    slots.put(name, slot);
    slotAndInitExprs.add(SNodeFactory.createSlotInitialization(slot, init));
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

  public ClassDefinition assemble() {
    // to prepare the class definition we need to assemble:
    //   - the class instantiation method, which resolves super
    //   - the primary factory method, which allocates the object,
    //     and then calls initiation
    //   - the initialization method, which class super, and then initializes the object

    SMethod classObjectInstantiation = assembleClassObjectInstantiationMethod();
    SMethod primaryFactory = assemblePrimaryFactoryMethod();
    SMethod initializationMethod = assembleInitializationMethod();
    factoryMethods.add(primaryFactory);
    methods.add(initializationMethod);

    ClassDefinition clsDef = new ClassDefinition(name, classObjectInstantiation,
        methods.toArray(new SMethod[0]), factoryMethods.toArray(new SMethod[0]));

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
    return (SMethod) instantiation.assemble(body, AccessModifier.NOT_APPLICABLE, null, null);
  }

  private SMethod assemblePrimaryFactoryMethod() {
    MethodBuilder builder = new MethodBuilder(this);
    builder.setSignature(primaryFactoryMethodName);

    builder.addArgumentIfAbsent("self");

    // first create new Object
    ExpressionNode newObject = NewObjectPrimFactory.create(
        builder.getReadNode("self", null));

    List<ExpressionNode> args = createFactoryMethodArgumentRead(builder,
        newObject);

    // This is a bet on initializer methods being constructed well,
    // so that they return self
    ExpressionNode initializedObject = SNodeFactory.createMessageSend(
        getInitializerName(primaryFactoryMethodName), args, null);

    return (SMethod) builder.assemble(initializedObject, AccessModifier.PROTECTED,
        Symbols.symbolFor("initialization"), null);
  }

  private SMethod assembleInitializationMethod() {
    // first, we need to do a super send to the primary factor method
    List<ExpressionNode> args = createFactoryMethodArgumentRead(initializer,
        initializer.getReadNode("super", null));
    ExpressionNode superInit = SNodeFactory.createMessageSend(
        getInitializerName(primaryFactoryMethodNameOfSuperClass), args, null);

    // after the initialization by the super class is finished, we need to
    // evaluate the slot and init expressions
    List<ExpressionNode> allExprs = new ArrayList<ExpressionNode>(1 + slotAndInitExprs.size());
    allExprs.add(superInit);
    allExprs.addAll(slotAndInitExprs);
    ExpressionNode body = SNodeFactory.createSequence(allExprs, null);
    return (SMethod) initializer.assemble(body, AccessModifier.PROTECTED,
        Symbols.symbolFor("initialization"), null);
  }

  protected List<ExpressionNode> createFactoryMethodArgumentRead(
      final MethodBuilder builder, final ExpressionNode receiver) {
    // then, call the initializer on it
    List<ExpressionNode> args = new ArrayList<>(
        primaryFactoryMethodName.getNumberOfSignatureArguments());

    args.add(receiver);

    if (primaryFactoryArguments != null) {
      for (String arg : primaryFactoryArguments) {
        builder.addArgumentIfAbsent(arg.toString());
        args.add(builder.getReadNode(arg, null));
      }
    }
    return args;
  }

  private SSymbol getInitializerName(final SSymbol selector) {
    return Symbols.symbolFor("initializer`" + selector.getString());
  }

  @Override
  public String toString() {
    return "ClassGenC(" + name.getString() + ")";
  }
}
