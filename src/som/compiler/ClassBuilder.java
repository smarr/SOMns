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
import som.vm.NotYetImplementedException;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class ClassBuilder {

  public ClassBuilder(final MethodBuilder instMethod) {
    this.instantiation = instMethod;
    this.initializer   = new MethodBuilder(this);
  }

  private final MethodBuilder instantiation;
  private final MethodBuilder initializer;

  private final ArrayList<ExpressionNode> slotAndInitExprs = new ArrayList<>();

  private SSymbol             name;
  private AbstractMessageSendNode superclassResolution;
  private final LinkedHashMap<SSymbol, SlotDefinition> slots = new LinkedHashMap<>();
  private final List<SInvokable> methods = new ArrayList<SInvokable>();
  private final List<SInvokable> factoryMethods  = new ArrayList<SInvokable>();
  private SInvokable primaryFactoryMethod;

  public void setName(final SSymbol name) {
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

  public void addMethod(final SInvokable meth) {
    methods.add(meth);
  }

  public void addFactoryMethod(final SInvokable meth) {
    factoryMethods.add(meth);
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

  public byte getFieldIndex(final SSymbol field) {
    throw new UnsupportedOperationException("Don't think we can do this");
//    return (byte) slots.indexOf(field);
  }

  public boolean isClassSide() {
    throw new UnsupportedOperationException("This is not supported anymore. Should not be necessary");
  }

  public ClassDefinition assemble() {
    throw new NotYetImplementedException();
//    // build class class name
//    String ccname = name.getString() + " class";
//
//    // Load the super class
//    SClass superClass = null; // TODO: // universe.loadClass(superName);
//
//    // Allocate the class of the resulting class
//    SClass resultClass = Universe.newClass(Classes.metaclassClass);
//
//    // Initialize the class of the resulting class
//    resultClass.setInstanceInvokables(
//        SArray.create(factoryMethods.toArray(new Object[0])));
//    resultClass.setName(Symbols.symbolFor(ccname));
//
//    SClass superMClass = superClass.getSOMClass();
//    resultClass.setSuperClass(superMClass);
//
//    // Allocate the resulting class
//    SClass result = Universe.newClass(resultClass);
//
//    // Initialize the resulting class
//    result.setName(name);
//    result.setSuperClass(superClass);
//    result.setInstanceFields(
//        SArray.create(slots.toArray(new Object[0])));
//    result.setInstanceInvokables(
//        SArray.create(methods.toArray(new Object[0])));
//
//    return result;
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

  @Override
  public String toString() {
    return "ClassGenC(" + name.getString() + ")";
  }
}
