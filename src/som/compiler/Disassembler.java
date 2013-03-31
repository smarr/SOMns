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

package som.compiler;

import som.interpreter.Bytecodes;
import som.vmobjects.Class;
import som.vmobjects.Invokable;
import som.vmobjects.Method;
import som.vmobjects.Object;
import som.vmobjects.Symbol;

public class Disassembler {
	
	public static void dump(Class cl) {
		for(int i = 0; i < cl.getNumberOfInstanceInvokables(); i++) {
			Invokable inv = cl.getInstanceInvokable(i);
			// output header and skip if the Invokable is a Primitive
			System.err.print(cl.getName().toString() + ">>" + inv.getSignature().toString() + " = ");
			if(inv.isPrimitive()) {
				System.err.println("<primitive>");
				continue;
			}
			// output actual method
			dumpMethod((Method) inv, "\t");
		}
	}
	
	public static void dumpMethod(Method m, java.lang.String indent) {
		System.err.println("(");
		// output stack information
		System.err.println(indent + "<" + m.getNumberOfLocals() + " locals, " + m.getMaximumNumberOfStackElements() + " stack, " + m.getNumberOfBytecodes() + " bc_count>");
		// output bytecodes
		for(int b = 0; b < m.getNumberOfBytecodes(); b += Bytecodes.getBytecodeLength(m.getBytecode(b))) {
			System.err.print(indent);
			// bytecode index
			if(b < 10) System.err.print(' ');
			if(b < 100) System.err.print(' ');
			System.err.print(" " + b + ":");
			// mnemonic
			byte bytecode = m.getBytecode(b);
			System.err.print(Bytecodes.bytecodeNames[bytecode] + "  ");
			// parameters (if any)
			if(Bytecodes.getBytecodeLength(bytecode) == 1) {
				System.err.println();
				continue;
			}
			switch(bytecode) {
			case Bytecodes.push_local:
				System.err.println("local: " + m.getBytecode(b+1) + ", context: " + m.getBytecode(b+2));
				break;
			case Bytecodes.push_argument:
				System.err.println("argument: " + m.getBytecode(b+1) + ", context " + m.getBytecode(b+2));
				break;
			case Bytecodes.push_field:
				System.err.println("(index: " + m.getBytecode(b+1) + ") field: " + ((Symbol) m.getConstant(b)).toString());
				break;
			case Bytecodes.push_block:
				System.err.print("block: (index: " + m.getBytecode(b+1) + ") ");
				dumpMethod((Method) m.getConstant(b), indent + "\t");
				break;
			case Bytecodes.push_constant:
				Object constant = m.getConstant(b);
				System.err.println("(index: " + m.getBytecode(b+1) + ") value: " + "(" + constant.getSOMClass().getName().toString() + ") " + constant.toString());
				break;
			case Bytecodes.push_global:
				System.err.println("(index: " + m.getBytecode(b+1) + ") value: " + ((Symbol) m.getConstant(b)).toString());
				break;
			case Bytecodes.pop_local:
				System.err.println("local: " + m.getBytecode(b+1) + ", context: " + m.getBytecode(b+2));
				break;
			case Bytecodes.pop_argument:
				System.err.println("argument: " + m.getBytecode(b+1) + ", context: " + m.getBytecode(b+2));
				break;
			case Bytecodes.pop_field:
				System.err.println("(index: " + m.getBytecode(b+1) + ") field: " + ((Symbol) m.getConstant(b)).toString());
				break;
			case Bytecodes.send:
				System.err.println("(index: " + m.getBytecode(b+1) + ") signature: " + ((Symbol) m.getConstant(b)).toString());
				break;
			case Bytecodes.super_send:
				System.err.println("(index: " + m.getBytecode(b+1) + ") signature: " + ((Symbol) m.getConstant(b)).toString());
				break;
			default:
				System.err.println("<incorrect bytecode>");
			}
		}
		System.err.println(indent + ")");
	}
	
}
