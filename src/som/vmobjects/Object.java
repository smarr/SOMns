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

import som.interpreter.Interpreter;
import som.vm.Universe;

public class Object
{
  public Object()
  {
    // Set the number of fields to the default value
    setNumberOfFields(getDefaultNumberOfFields());
  }
  
  public Object(int numberOfFields)
  {
    // Set the number of fields to the given value
    setNumberOfFields(numberOfFields);
  }
  
  public Class getSOMClass()
  {
    // Get the class of this object by reading the field with class index
    return (Class) getField(classIndex);
  }

  public void setClass(Class value)
  {
    // Set the class of this object by writing to the field with class index
    setField(classIndex, value);
  }
  
  public Symbol getFieldName(int index)
  {
    // Get the name of the field with the given index
    return getSOMClass().getInstanceFieldName(index);
  }
  
  public int getFieldIndex(Symbol name)
  {
    // Get the index for the field with the given name
    return getSOMClass().lookupFieldIndex(name);
  }
  
  public int getNumberOfFields()
  {
    // Get the number of fields in this object
    return fields.length;
  }
  
  public void setNumberOfFields(int value)
  {
    // Allocate a new array of fields
    fields = new Object[value];
    
    // Clear each and every field by putting nil into them
    for (int i = 0; i < getNumberOfFields(); i++) {
      setField(i, Universe.nilObject);
    }
  }
  
  public int getDefaultNumberOfFields()
  {
    // Return the default number of fields in an object
    return numberOfObjectFields;
  }

  public void send(java.lang.String selectorString, Object[] arguments) 
  {
    // Turn the selector string into a selector
    Symbol selector = Universe.symbolFor(selectorString);

    // Push the receiver onto the stack
    Interpreter.getFrame().push(this);

    // Push the arguments onto the stack
    for(Object arg : arguments)
        Interpreter.getFrame().push(arg);
    
    // Lookup the invokable 
    Invokable invokable = getSOMClass().lookupInvokable(selector);

    // Invoke the invokable
    invokable.invoke(Interpreter.getFrame());
  }

  public Object getField(int index)
  {
    // Get the field with the given index
    return fields[index];
  }

  public void setField(int index, Object value)
  {
    // Set the field with the given index to the given value
    fields[index] = value;
  }
  
  public static void _assert(boolean value)
  {
    // Delegate to universal assertion routine
    Universe._assert(value);
  }
  
  // Private array of fields
  private Object[] fields;
  
  // Static field indices and number of object fields
  static final int classIndex             = 0;
  static final int numberOfObjectFields = 1 + classIndex;
}

