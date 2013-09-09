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

import som.vm.Universe;
import som.vmobjects.Symbol;

public class ClassGenerationContext {

  private final Universe universe;

  public ClassGenerationContext(final Universe universe) {
    this.universe = universe;
  }

  private som.vmobjects.Symbol          name;
  private som.vmobjects.Symbol          superName;
  private boolean                       classSide;
  private int                           numberOfInstanceFieldsOfSuper;
  private int                           numberOfClassFieldsOfSuper;
  private List<som.vmobjects.Object>    instanceFields  = new ArrayList<som.vmobjects.Object>();
  private List<som.vmobjects.Invokable> instanceMethods = new ArrayList<som.vmobjects.Invokable>();
  private List<som.vmobjects.Object>    classFields     = new ArrayList<som.vmobjects.Object>();
  private List<som.vmobjects.Invokable> classMethods    = new ArrayList<som.vmobjects.Invokable>();

  public void setName(final Symbol name) {
    this.name = name;
  }

  public Symbol getName() {
    return name;
  }

  public void setSuperName(final Symbol superName) {
    this.superName = superName;
  }

  public void setNumberOfInstanceFieldsOfSuper(int numberOfInstanceFields) {
    this.numberOfInstanceFieldsOfSuper = numberOfInstanceFields;
  }

  public void setNumberOfClassFieldsOfSuper(int numberOfClassFields) {
    this.numberOfClassFieldsOfSuper = numberOfClassFields;
  }

  public void addInstanceMethod(final som.vmobjects.Invokable meth) {
    instanceMethods.add(meth);
  }

  public void setClassSide(boolean b) {
    classSide = b;
  }

  public void addClassMethod(final som.vmobjects.Invokable meth) {
    classMethods.add(meth);
  }

  public void addInstanceField(final Symbol field) {
    instanceFields.add(field);
  }

  public void addClassField(final Symbol field) {
    classFields.add(field);
  }

  public boolean hasField(final Symbol field) {
    return (isClassSide() ? classFields : instanceFields).contains(field);
  }

  public byte getFieldIndex(final Symbol field) {
    int localIndex = (isClassSide() ? classFields : instanceFields).indexOf(field);
    return (byte) (localIndex + (isClassSide() ? numberOfClassFieldsOfSuper : numberOfInstanceFieldsOfSuper));
  }

  public boolean isClassSide() {
    return classSide;
  }

  public som.vmobjects.Class assemble() {
    // build class class name
    String ccname = name.getString() + " class";

    // Load the super class
    som.vmobjects.Class superClass = universe.loadClass(superName);

    // Allocate the class of the resulting class
    som.vmobjects.Class resultClass = universe.newClass(universe.metaclassClass);

    // Initialize the class of the resulting class
    resultClass.setInstanceFields(universe.newArray(classFields));
    resultClass.setInstanceInvokables(universe.newArray(classMethods));
    resultClass.setName(universe.symbolFor(ccname));

    som.vmobjects.Class superMClass = superClass.getSOMClass();
    resultClass.setSuperClass(superMClass);

    // Allocate the resulting class
    som.vmobjects.Class result = universe.newClass(resultClass);

    // Initialize the resulting class
    result.setName(name);
    result.setSuperClass(superClass);
    result.setInstanceFields(universe.newArray(instanceFields));
    result.setInstanceInvokables(universe.newArray(instanceMethods));

    return result;
  }

  public void assembleSystemClass(final som.vmobjects.Class systemClass) {
    systemClass.setInstanceInvokables(universe.newArray(instanceMethods));
    systemClass.setInstanceFields(universe.newArray(instanceFields));
    // class-bound == class-instance-bound
    som.vmobjects.Class superMClass = systemClass.getSOMClass();
    superMClass.setInstanceInvokables(universe.newArray(classMethods));
    superMClass.setInstanceFields(universe.newArray(classFields));
  }

}
