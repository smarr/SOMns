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

package som.vm;

import som.interpreter.Interpreter;
import som.vmobjects.Class;
import som.vmobjects.Frame;
import som.vmobjects.Invokable;
import som.vmobjects.Method;
import som.vmobjects.Object;

public class Shell {

    private static Method bootstrapMethod;

    public static void setBootstrapMethod(Method method) {
	bootstrapMethod = method;
    }

    public static void start() {

	java.io.BufferedReader in;
	java.lang.String stmt;
	int counter, bytecodeIndex;
	Class myClass;
	Object myObject, it;
	Frame currentFrame;	

	counter = 0;
	in = new java.io.BufferedReader(new java.io.InputStreamReader(java.lang.System.in));
	it = Universe.nilObject;

	System.out.println("SOM Shell. Type \"quit\" to exit.\n");


	// Create a fake bootstrap frame
	currentFrame = Interpreter.pushNewFrame(bootstrapMethod);

	// Remember the first bytecode index, e.g. index of the halt instruction
	bytecodeIndex = currentFrame.getBytecodeIndex();

	while (true) {
	    try {
		System.out.print("---> ");
		// Read a statement from the keyboard
		stmt = in.readLine();
		if (stmt.equals("quit")) return;

		// Generate a temporary class with a run method
		stmt = "Shell_Class_" + counter++ + " = ( run: it = ( | tmp | tmp := (" + stmt + " ). 'it = ' print. ^tmp println ) )";

		// Compile and load the newly generated class
		myClass = Universe.loadShellClass(stmt);

		// If success
		if (myClass != null) {
		    currentFrame = Interpreter.getFrame();

		    // Go back, so we will evaluate the bootstrap frames halt instruction again
		    currentFrame.setBytecodeIndex(bytecodeIndex);

		    // Create and push a new instance of our class on the stack
		    myObject = Universe.newInstance(myClass);
		    currentFrame.push(myObject);

		    // Push the old value of "it" on the stack
		    currentFrame.push(it);

		    // Lookup the run: method
		    Invokable initialize = myClass.lookupInvokable(Universe.symbolFor("run:"));

		    // Invoke the run method
		    initialize.invoke(currentFrame);

		    // Start the interpreter
		    Interpreter.start();

		    // Save the result of the run method
		    it = currentFrame.pop();
		}
	    } catch (Exception e) {
		System.out.println("Caught exception: "+e.getMessage());
		System.out.println(""+Interpreter.getFrame().getPreviousFrame());
	    }
	}
    }
}
