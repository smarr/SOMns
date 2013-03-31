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

import som.vm.Universe;
import som.vmobjects.Class;
import som.vmobjects.Frame;
import som.vmobjects.Integer;
import som.vmobjects.Object;
import som.vmobjects.Primitive;
import som.vmobjects.String;
import som.vmobjects.Symbol;

public class SystemPrimitives extends Primitives 
{ 
  public void installPrimitives() 
  {
    installInstancePrimitive
      (new Primitive("load:")
        {
          public void invoke(Frame frame)
          {
            Symbol argument = (Symbol) frame.pop();
            frame.pop(); // not required
            Class result = Universe.loadClass(argument);
            frame.push(result != null ? result : Universe.nilObject);
          }
        }
       );

    installInstancePrimitive
      (new Primitive("exit:")
        {
          public void invoke(Frame frame)
          {
            Integer error = (Integer) frame.pop();
            Universe.exit(error.getEmbeddedInteger());
          }
        }
       );
    
    installInstancePrimitive
      (new Primitive("global:")
        {
          public void invoke(Frame frame)
          {
            Symbol argument = (Symbol) frame.pop();
            frame.pop(); // not required
            Object result = Universe.getGlobal(argument);
            frame.push(result != null ? result : Universe.nilObject);
          }
        }
       );
    
    installInstancePrimitive
      (new Primitive("global:put:")
        {
          public void invoke(Frame frame)
          {
            Object value = frame.pop();
            Symbol argument = (Symbol) frame.pop();
            Universe.setGlobal(argument, value);
          }
        }
       );
    
    installInstancePrimitive
      (new Primitive("printString:")
        {
          public void invoke(Frame frame)
          {
            String argument = (String) frame.pop();
            System.out.print(argument.getEmbeddedString());
          }
        }
       );
    
    installInstancePrimitive
      (new Primitive("printNewline")
        {
          public void invoke(Frame frame)
          {
            System.out.println("");
          }
        }
       );
    
    startMicroTime = System.nanoTime() / 1000L;
    startTime = startMicroTime / 1000L;
    installInstancePrimitive
      (new Primitive("time")
        {
          public void invoke(Frame frame)
          {
            frame.pop(); // ignore
            int time = (int) (System.currentTimeMillis() - startTime);
            frame.push(Universe.newInteger(time));
          }
        }
       );
    
    installInstancePrimitive
    (new Primitive("ticks")
      {
        public void invoke(Frame frame)
        {
          frame.pop(); // ignore
          int time = (int) (System.nanoTime() / 1000L - startMicroTime);
          frame.push(Universe.newInteger(time));
        }
      }
     );
    
    installInstancePrimitive(
            new Primitive("fullGC") {
                public void invoke(Frame frame) {
                    // naught - GC is entirely left to the JVM
                }
            }
        );
    
  }

  private static long startTime;
  private static long startMicroTime;
}