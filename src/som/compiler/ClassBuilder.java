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
import java.util.List;

import som.vm.Symbols;
import som.vm.Universe;
import som.vm.constants.Classes;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class ClassBuilder {

  private final Universe universe;

  public ClassBuilder(final Universe universe) {
    this.universe = universe;
  }

  private SSymbol             name;
  private SSymbol             superName;
  private final List<SSymbol>    slots  = new ArrayList<SSymbol>();
  private final List<SInvokable> methods = new ArrayList<SInvokable>();
  private final List<SInvokable> factoryMethods  = new ArrayList<SInvokable>();
  private SInvokable primaryFactoryMethod;

  public void setName(final SSymbol name) {
    this.name = name;
  }

  public SSymbol getName() {
    return name;
  }

  public void setSuperName(final SSymbol superName) {
    this.superName = superName;
  }

  public void setSlotsOfSuper(final SArray fieldNames) {
    for (int i = 0; i < fieldNames.getObjectStorage().length; i++) {
      slots.add((SSymbol) fieldNames.getObjectStorage()[i]);
    }
  }

  public void addMethod(final SInvokable meth) {
    methods.add(meth);
  }

  public void addFactoryMethod(final SInvokable meth) {
    factoryMethods.add(meth);
  }

  public void addSlot(final SSymbol field) {
    slots.add(field);
  }

  public boolean hasSlot(final SSymbol field) {
    return slots.contains(field);
  }

  public byte getFieldIndex(final SSymbol field) {
    throw new UnsupportedOperationException("Don't think we can do this");
//    return (byte) slots.indexOf(field);
  }

  public boolean isClassSide() {
    throw new UnsupportedOperationException("This is not supported anymore. Should not be necessary");
  }

  @TruffleBoundary
  public SClass assemble() {
    // build class class name
    String ccname = name.getString() + " class";

    // Load the super class
    SClass superClass = universe.loadClass(superName);

    // Allocate the class of the resulting class
    SClass resultClass = universe.newClass(Classes.metaclassClass);

    // Initialize the class of the resulting class
    resultClass.setInstanceInvokables(
        SArray.create(factoryMethods.toArray(new Object[0])));
    resultClass.setName(Symbols.symbolFor(ccname));

    SClass superMClass = superClass.getSOMClass();
    resultClass.setSuperClass(superMClass);

    // Allocate the resulting class
    SClass result = universe.newClass(resultClass);

    // Initialize the resulting class
    result.setName(name);
    result.setSuperClass(superClass);
    result.setInstanceFields(
        SArray.create(slots.toArray(new Object[0])));
    result.setInstanceInvokables(
        SArray.create(methods.toArray(new Object[0])));

    return result;
  }

  @TruffleBoundary
  public void assembleSystemClass(final SClass systemClass) {
    systemClass.setInstanceInvokables(
        SArray.create(methods.toArray(new Object[0])));
    systemClass.setInstanceFields(
        SArray.create(slots.toArray(new Object[0])));
    // class-bound == class-instance-bound
    SClass superMClass = systemClass.getSOMClass();
    superMClass.setInstanceInvokables(
        SArray.create(factoryMethods.toArray(new Object[0])));
  }

  @Override
  public String toString() {
    return "ClassGenC(" + name.getString() + ")";
  }
}
