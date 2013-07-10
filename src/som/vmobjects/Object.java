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

import com.oracle.truffle.api.frame.PackedFrame;
import som.vm.Universe;

public class Object {

  public Object(final Object nilObject) {
    // Set the number of fields to the default value
    setNumberOfFieldsAndClear(getDefaultNumberOfFields(), nilObject);
  }

  public Object(int numberOfFields, final Object nilObject) {
    // Set the number of fields to the given value
    setNumberOfFieldsAndClear(numberOfFields, nilObject);
  }

  public Class getSOMClass() {
    // Get the class of this object by reading the field with class index
    return (Class) getField(classIndex);
  }

  public void setClass(Class value) {
    // Set the class of this object by writing to the field with class index
    setField(classIndex, value);
  }

  public Symbol getFieldName(int index) {
    // Get the name of the field with the given index
    return getSOMClass().getInstanceFieldName(index);
  }

  public int getFieldIndex(Symbol name) {
    // Get the index for the field with the given name
    return getSOMClass().lookupFieldIndex(name);
  }

  public int getNumberOfFields() {
    // Get the number of fields in this object
    return fields.length;
  }

  public void setNumberOfFieldsAndClear(int value, final Object nilObject) {
    // Allocate a new array of fields
    fields = new Object[value];

    // Clear each and every field by putting nil into them
    for (int i = 0; i < getNumberOfFields(); i++) {
      setField(i, nilObject);
    }
  }

  public int getDefaultNumberOfFields() {
    // Return the default number of fields in an object
    return numberOfObjectFields;
  }

  public Object send(java.lang.String selectorString, Object[] arguments,
      final Universe universe, final PackedFrame frame) {
    // Turn the selector string into a selector
    Symbol selector = universe.symbolFor(selectorString);

    // Lookup the invokable
    Invokable invokable = getSOMClass().lookupInvokable(selector);

    // Invoke the invokable
    return invokable.invoke(frame, this, arguments);
  }

  public Object sendDoesNotUnderstand(final Symbol selector,
      final Object[] arguments,
      final Universe universe,
      final PackedFrame frame) {
    // Allocate an array with enough room to hold all arguments
    Array argumentsArray = universe.newArray(arguments.length);
    for (int i = 0; i < arguments.length; i++) {
      argumentsArray.setIndexableField(i, arguments[i]);
    }

    Object[] args = new Object[] {selector, argumentsArray};

    return send("doesNotUnderstand:arguments:", args, universe, frame);
  }

  public Object sendUnknownGlobal(final Symbol globalName,
      final Universe universe,
      final PackedFrame frame) {
    Object[] arguments = {globalName};
    return send("unknownGlobal:", arguments, universe, frame);
  }

  public Object sendEscapedBlock(final Block block,
      final Universe universe,
      final PackedFrame frame) {
    Object[] arguments = {block};
    return send("escapedBlock:", arguments, universe, frame);
  }

  public Object getField(int index) {
    // Get the field with the given index
    return fields[index];
  }

  public void setField(int index, Object value) {
    // Set the field with the given index to the given value
    fields[index] = value;
  }

  @Override
  public java.lang.String toString() {
    return "a " + getSOMClass().getName().getString();
  }

  // Private array of fields
  private Object[] fields;

  // Static field indices and number of object fields
  static final int classIndex           = 0;
  static final int numberOfObjectFields = 1 + classIndex;
}
