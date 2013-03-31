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

package som.primitives;

import som.vmobjects.Class;
import som.vmobjects.Primitive;

public abstract class Primitives
{
  public final void installPrimitivesIn(Class value)
  {
    // Save a reference to the holder class
    holder = value;

    // Install the primitives from this primitives class
    installPrimitives();
  }

  public abstract void installPrimitives();

  protected void installInstancePrimitive(Primitive primitive)
  {
    // Install the given primitive as an instance primitive in the holder class
    holder.addInstancePrimitive(primitive);
  }

  protected void installClassPrimitive(Primitive primitive)
  {
    // Install the given primitive as an instance primitive in the class of the holder class
    holder.getSOMClass().addInstancePrimitive(primitive);
  }

  private Class holder;
}
