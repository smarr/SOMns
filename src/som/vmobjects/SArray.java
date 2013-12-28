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

import com.oracle.truffle.api.nodes.ExplodeLoop;

public final class SArray extends SAbstractObject {

  public SArray(final SObject nilObject, final int size) {
    super();
    // Allocate a new array of indexable fields
    indexableFields = new SAbstractObject[size];
    setNumberOfIndexableFieldsAndClear(size, nilObject);
  }

  public SAbstractObject getIndexableField(final int index) {
    // Get the indexable field with the given index
    return indexableFields[index];
  }

  public void setIndexableField(final int index, final SAbstractObject value) {
    // Set the indexable field with the given index to the given value
    indexableFields[index] = value;
  }

  public int getNumberOfIndexableFields() {
    // Get the number of indexable fields in this array
    return indexableFields.length;
  }

  @ExplodeLoop
  private void setNumberOfIndexableFieldsAndClear(final int value,
      final SObject nilObject) {
    // Clear each and every field by putting nil into them
    for (int i = 0; i < getNumberOfIndexableFields(); i++) {
      setIndexableField(i, nilObject);
    }
  }

  public SArray copyAndExtendWith(final SAbstractObject value, final Universe universe) {
    // Allocate a new array which has one indexable field more than this
    // array
    SArray result = universe.newArray(getNumberOfIndexableFields() + 1);

    // Copy the indexable fields from this array to the new array
    copyIndexableFieldsTo(result);

    // Insert the given object as the last indexable field in the new array
    result.setIndexableField(getNumberOfIndexableFields(), value);

    // Return the new array
    return result;
  }

  protected void copyIndexableFieldsTo(final SArray destination) {
    // Copy all indexable fields from this array to the destination array
    for (int i = 0; i < getNumberOfIndexableFields(); i++) {
      destination.setIndexableField(i, getIndexableField(i));
    }
  }

  @Override
  public SClass getSOMClass(final Universe universe) {
    return universe.arrayClass;
  }


  // array of indexable fields
  public final SAbstractObject[] indexableFields;
}
