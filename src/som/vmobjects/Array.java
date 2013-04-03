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

import som.vm.Universe;

public class Array extends Object {

  public Array(final Object nilObject) {
    super(nilObject);
  }

  public Object getIndexableField(int index) {
    // Get the indexable field with the given index
    return indexableFields[index];
  }

  public void setIndexableField(int index, Object value) {
    // Set the indexable field with the given index to the given value
    indexableFields[index] = value;
  }

  public int getNumberOfIndexableFields() {
    // Get the number of indexable fields in this array
    return indexableFields.length;
  }

  public void setNumberOfIndexableFieldsAndClear(int value,
      final Object nilObject) {
    // Allocate a new array of indexable fields
    indexableFields = new Object[value];

    // Clear each and every field by putting nil into them
    for (int i = 0; i < getNumberOfIndexableFields(); i++) {
      setIndexableField(i, nilObject);
    }
  }

  public Array copyAndExtendWith(Object value, final Universe universe) {
    // Allocate a new array which has one indexable field more than this
    // array
    Array result = universe.newArray(getNumberOfIndexableFields() + 1);

    // Copy the indexable fields from this array to the new array
    copyIndexableFieldsTo(result);

    // Insert the given object as the last indexable field in the new array
    result.setIndexableField(getNumberOfIndexableFields(), value);

    // Return the new array
    return result;
  }

  protected void copyIndexableFieldsTo(Array destination) {
    // Copy all indexable fields from this array to the destination array
    for (int i = 0; i < getNumberOfIndexableFields(); i++) {
      destination.setIndexableField(i, getIndexableField(i));
    }
  }

  // Private array of indexable fields
  public Object[] indexableFields;
}
