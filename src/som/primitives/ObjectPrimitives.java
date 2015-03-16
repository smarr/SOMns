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

package som.primitives;

import som.primitives.ObjectPrimsFactory.ClassPrimFactory;
import som.primitives.ObjectPrimsFactory.HaltPrimFactory;
import som.primitives.ObjectPrimsFactory.InstVarAtPrimFactory;
import som.primitives.ObjectPrimsFactory.InstVarAtPutPrimFactory;
import som.primitives.ObjectPrimsFactory.InstVarNamedPrimFactory;
import som.primitives.reflection.PerformInSuperclassPrimFactory;
import som.primitives.reflection.PerformPrimFactory;
import som.primitives.reflection.PerformWithArgumentsInSuperclassPrimFactory;
import som.primitives.reflection.PerformWithArgumentsPrimFactory;

public final class ObjectPrimitives extends Primitives {
  public ObjectPrimitives(final boolean displayWarning) { super(displayWarning); }

  @Override
  public void installPrimitives() {

    installInstancePrimitive("==",                     EqualsEqualsPrimFactory.getInstance());
    installInstancePrimitive("hashcode",               HashPrimFactory.getInstance());
    installInstancePrimitive("objectSize",             ObjectSizePrimFactory.getInstance());
    installInstancePrimitive("perform:",               PerformPrimFactory.getInstance());
    installInstancePrimitive("perform:inSuperclass:",  PerformInSuperclassPrimFactory.getInstance());
    installInstancePrimitive("perform:withArguments:", PerformWithArgumentsPrimFactory.getInstance());
    installInstancePrimitive("perform:withArguments:inSuperclass:", PerformWithArgumentsInSuperclassPrimFactory.getInstance());
    installInstancePrimitive("instVarAt:",             InstVarAtPrimFactory.getInstance());
    installInstancePrimitive("instVarAt:put:",         InstVarAtPutPrimFactory.getInstance());
    installInstancePrimitive("instVarNamed:",          InstVarNamedPrimFactory.getInstance());
    installInstancePrimitive("halt",                   HaltPrimFactory.getInstance());
    installInstancePrimitive("class",                  ClassPrimFactory.getInstance());
  }
}
