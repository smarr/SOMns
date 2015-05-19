/**
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

package som.vmobjects;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;

import java.lang.reflect.Constructor;

import som.interpreter.objectstorage.ObjectLayout;
import som.primitives.Primitives;
import som.vm.Universe;
import som.vm.constants.Nil;
import som.vmobjects.SInvokable.SPrimitive;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class SClass extends SObjectWithoutFields {

  @CompilationFinal private SObjectWithoutFields superclass;
  @CompilationFinal private SSymbol name;
  @CompilationFinal private SArray  instanceInvokables;
  @CompilationFinal private SArray  instanceFields;

  @CompilationFinal private ObjectLayout layoutForInstances;

  public SClass() {
    // Initialize this class by calling the super constructor with the given
    // value
    this.superclass = Nil.nilObject;
  }

  public SClass(final SClass clazz) {
    super(clazz);
    this.superclass = Nil.nilObject;
  }

  public SObjectWithoutFields getSuperClass() {
    return superclass;
  }

  public void setSuperClass(final SClass value) {
    transferToInterpreterAndInvalidate("SClass.setSuperClass");
    superclass = value;
  }

  public boolean hasSuperClass() {
    return superclass != Nil.nilObject;
  }

  public SSymbol getName() {
    return name;
  }

  public void setName(final SSymbol value) {
    transferToInterpreterAndInvalidate("SClass.setName");
    name = value;
  }

  public SArray getInstanceFields() {
    return instanceFields;
  }

  public void setInstanceFields(final SArray fields) {
    transferToInterpreterAndInvalidate("SClass.setInstanceFields");
    instanceFields = fields;
    if (layoutForInstances == null ||
        instanceFields.getObjectStorage().length != layoutForInstances.getNumberOfFields()) {
      layoutForInstances = new ObjectLayout(
          fields.getObjectStorage().length, this);
    }
  }

  public SArray getInstanceInvokables() {
    return instanceInvokables;
  }

  public void setInstanceInvokables(final SArray value) {
    transferToInterpreterAndInvalidate("SClass.setInstanceInvokables");
    instanceInvokables = value;

    // Make sure this class is the holder of all invokables in the array
    for (int i = 0; i < getNumberOfInstanceInvokables(); i++) {
      ((SInvokable) instanceInvokables.getObjectStorage()[i]).setHolder(this);
    }
  }

  public int getNumberOfInstanceInvokables() {
    // Return the number of instance invokables in this class
    return instanceInvokables.getObjectStorage().length;
  }

  public SInvokable getInstanceInvokable(final int index) {
    return (SInvokable) instanceInvokables.getObjectStorage()[index];
  }

  public void setInstanceInvokable(final int index, final SInvokable value) {
    CompilerAsserts.neverPartOfCompilation();
    // Set this class as the holder of the given invokable
    value.setHolder(this);

    instanceInvokables.getObjectStorage()[index] = value;

  }

  @TruffleBoundary
  public SInvokable lookupInvokable(final SSymbol selector) {
    SInvokable invokable;

    // Lookup invokable with given signature in array of instance invokables
    for (int i = 0; i < getNumberOfInstanceInvokables(); i++) {
      // Get the next invokable in the instance invokable array
      invokable = getInstanceInvokable(i);

      // Return the invokable if the signature matches
      if (invokable.getSignature() == selector) {
        return invokable;
      }
    }

    // Traverse the super class chain by calling lookup on the super class
    if (hasSuperClass()) {
      invokable = ((SClass) getSuperClass()).lookupInvokable(selector);
      if (invokable != null) {
        return invokable;
      }
    }

    // Invokable not found
    return null;
  }


    }

    }
  }

  public SSymbol getInstanceFieldName(final int index) {
    return (SSymbol) instanceFields.getObjectStorage()[index];
  }

  public int getNumberOfInstanceFields() {
    return instanceFields.getObjectStorage().length;
  }

  public ObjectLayout getLayoutForInstances() {
    return layoutForInstances;
  }

  public ObjectLayout updateInstanceLayoutWithInitializedField(final long index, final Class<?> type) {
    ObjectLayout updated = layoutForInstances.withInitializedField(index, type);

    if (updated != layoutForInstances) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      layoutForInstances = updated;
    }
    return layoutForInstances;
  }

  public ObjectLayout updateInstanceLayoutWithGeneralizedField(final long index) {
    ObjectLayout updated = layoutForInstances.withGeneralizedField(index);

    if (updated != layoutForInstances) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      layoutForInstances = updated;
    }
    return layoutForInstances;
  }

  @Override
  public String toString() {
    return "Class(" + getName().getString() + ")";
  }
}
