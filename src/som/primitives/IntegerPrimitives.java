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
import som.vmobjects.BigInteger;
import som.vmobjects.Double;
import som.vmobjects.Frame;
import som.vmobjects.Integer;
import som.vmobjects.Object;
import som.vmobjects.Primitive;
import som.interpreter.Interpreter;

public class IntegerPrimitives extends Primitives 
{   
	public IntegerPrimitives(final Universe universe) {
		super(universe);
	}
	
    private void pushLongResult(Frame frame, long result)
    {
	// Check with integer bounds and push:
	if (result > java.lang.Integer.MAX_VALUE || result < java.lang.Integer.MIN_VALUE)
	    frame.push(universe.newBigInteger(result));
	else
	    frame.push(universe.newInteger((int)result));
    }
    
    private void resendAsBigInteger(java.lang.String operator, Integer left, BigInteger right)
    {
	// Construct left value as BigInteger:
	BigInteger leftBigInteger = universe.newBigInteger((long)left.getEmbeddedInteger());
	
	// Resend message:
	Object[] operands = new Object[1];
	operands[0] = right;
	
	leftBigInteger.send(operator, operands, universe, universe.getInterpreter());
    }
    
    void resendAsDouble(java.lang.String operator, Integer left, Double right) {
        Double leftDouble = universe.newDouble((double)left.getEmbeddedInteger());
        Object[] operands = new Object[] { right };
        leftDouble.send(operator, operands, universe, universe.getInterpreter());
    }
        
    public void installPrimitives()
    {
	installInstancePrimitive
	    (new Primitive("asString", universe)
		{
		    public void invoke(Frame frame, final Interpreter interpreter)
		    {
			Integer self = (Integer) frame.pop();
			frame.push(universe.newString(java.lang.Integer.toString(self.getEmbeddedInteger())));
		    }
		}
	     );
	
	installInstancePrimitive(
			new Primitive("sqrt", universe) {
				public void invoke(Frame frame, final Interpreter interpreter) {
					Integer self = (Integer) frame.pop();
					frame.push(universe.newDouble(Math.sqrt((double) self.getEmbeddedInteger())));
				}
			}
		);
	
	installInstancePrimitive(
			new Primitive("atRandom", universe) {
				public void invoke(Frame frame, final Interpreter interpreter) {
					Integer self = (Integer) frame.pop();
					frame.push(universe.newInteger((int) ((double) self.getEmbeddedInteger() * Math.random())));
				}
			}
		);
    
	installInstancePrimitive
	    (new Primitive("+", universe)
		{
		    public void invoke(Frame frame, final Interpreter interpreter)
		    {
			Object rightObj = frame.pop();
			Integer left = (Integer) frame.pop();
			
			// Check second parameter type:
			if (rightObj instanceof BigInteger) {
			    // Second operand was BigInteger
			    resendAsBigInteger("+", left, (BigInteger)rightObj);
			} else if(rightObj instanceof Double) {
			    resendAsDouble("+", left, (Double)rightObj);
			} else {
			    // Do operation:
			    Integer right = (Integer)rightObj;
			    
			    long result = ((long)left.getEmbeddedInteger()) + right.getEmbeddedInteger();
			    pushLongResult(frame, result);
			}
		    }
		}
	     );
    
	installInstancePrimitive
	    (new Primitive("-", universe)
		{
		    public void invoke(Frame frame, final Interpreter interpreter)
		    {
			Object rightObj = frame.pop();
			Integer left = (Integer) frame.pop();
			
			// Check second parameter type:
			if (rightObj instanceof BigInteger) {
			    // Second operand was BigInteger
			    resendAsBigInteger("-", left, (BigInteger)rightObj);
			} else if(rightObj instanceof Double) {
			    resendAsDouble("-", left, (Double)rightObj);
			} else {
			    // Do operation:
			    Integer right = (Integer)rightObj;
			    
			    long result = ((long)left.getEmbeddedInteger()) - right.getEmbeddedInteger();
			    pushLongResult(frame, result);
			}
		    }
		}
	     );
    
	installInstancePrimitive
	    (new Primitive("*", universe)
		{
		    public void invoke(Frame frame, final Interpreter interpreter)
		    {
			Object rightObj = frame.pop();
			Integer left = (Integer) frame.pop();
			
			// Check second parameter type:
			if (rightObj instanceof BigInteger) {
			    // Second operand was BigInteger
			    resendAsBigInteger("*", left, (BigInteger)rightObj);
			} else if(rightObj instanceof Double) {
			    resendAsDouble("*", left, (Double)rightObj);
			} else {
			    // Do operation:
			    Integer right = (Integer)rightObj;
			    
			    long result = ((long)left.getEmbeddedInteger()) * right.getEmbeddedInteger();
			    pushLongResult(frame, result);
			}
		    }
		}
	     );
    
	installInstancePrimitive
	    (new Primitive("//", universe)
		{
		    public void invoke(Frame frame, final Interpreter interpreter)
		    {
			/*
			Integer op1 = (Integer) frame.pop();
			Integer op2 = (Integer) frame.pop();
			frame.push(universe.new_double((double)op2.get_embedded_integer() / (double)op1.get_embedded_integer()));
			*/
			Object rightObj = frame.pop();
			Integer left = (Integer) frame.pop();
			
			// Check second parameter type:
			if (rightObj instanceof BigInteger) {
			    // Second operand was BigInteger
			    resendAsBigInteger("/", left, (BigInteger)rightObj);
			} else if(rightObj instanceof Double) {
                resendAsDouble("/", left, (Double)rightObj);
            } else {
			    // Do operation:
			    Integer right = (Integer)rightObj;
			    
			    double result = ((double)left.getEmbeddedInteger()) / right.getEmbeddedInteger();
			    frame.push(universe.newDouble(result));
			}
		    }
		}
	     );
	
	installInstancePrimitive
	    (new Primitive("/", universe)
		{
		    public void invoke(Frame frame, final Interpreter interpreter)
		    {
			Object rightObj = frame.pop();
			Integer left = (Integer) frame.pop();
			
			// Check second parameter type:
			if (rightObj instanceof BigInteger) {
			    // Second operand was BigInteger
			    resendAsBigInteger("/", left, (BigInteger)rightObj);
			} else if(rightObj instanceof Double) {
                resendAsDouble("/", left, (Double)rightObj);
            } else {
			    // Do operation:
			    Integer right = (Integer)rightObj;
			    
			    long result = ((long)left.getEmbeddedInteger()) / right.getEmbeddedInteger();
			    pushLongResult(frame, result);
			}
		    }
		}
	     );

	installInstancePrimitive
	    (new Primitive("%", universe)
		{
		    public void invoke(Frame frame, final Interpreter interpreter)
		    {
			Object rightObj = frame.pop();
			Integer left = (Integer) frame.pop();
			
			// Check second parameter type:
			if (rightObj instanceof BigInteger) {
			    // Second operand was BigInteger
			    resendAsBigInteger("%", left, (BigInteger)rightObj);
			} else if(rightObj instanceof Double) {
                resendAsDouble("%", left, (Double)rightObj);
            } else {
			    // Do operation:
			    Integer right = (Integer)rightObj;
			    
			    long result = ((long)left.getEmbeddedInteger()) % right.getEmbeddedInteger();
			    pushLongResult(frame, result);
			}
		    }
		}
	     );

	installInstancePrimitive
	    (new Primitive("&", universe)
		{
		    public void invoke(Frame frame, final Interpreter interpreter)
		    {
			Object rightObj = frame.pop();
			Integer left = (Integer) frame.pop();
			
			// Check second parameter type:
			if (rightObj instanceof BigInteger) {
			    // Second operand was BigInteger
			    resendAsBigInteger("&", left, (BigInteger)rightObj);
			} else if(rightObj instanceof Double) {
                resendAsDouble("&", left, (Double)rightObj);
            } else {
			    // Do operation:
			    Integer right = (Integer)rightObj;
			    
			    long result = ((long)left.getEmbeddedInteger()) & right.getEmbeddedInteger();
			    pushLongResult(frame, result);
			}
		    }
		}
	     );
    
	installInstancePrimitive
	    (new Primitive("=", universe)
		{
		    public void invoke(Frame frame, final Interpreter interpreter)
		    {
			Object rightObj = frame.pop();
			Integer left = (Integer) frame.pop();
			
			// Check second parameter type:
			if (rightObj instanceof BigInteger) {
			    // Second operand was BigInteger:
			    resendAsBigInteger("=", left, (BigInteger)rightObj);
			} else if (rightObj instanceof Integer) {
			    // Second operand was Integer:
			    Integer right = (Integer)rightObj;
			    
			    if (left.getEmbeddedInteger() == right.getEmbeddedInteger())
				frame.push(universe.trueObject);
			    else
				frame.push(universe.falseObject);
			}
			else if (rightObj instanceof Double) {
			    // Second operand was Integer:
			    Double right = (Double)rightObj;
			    
			    if (left.getEmbeddedInteger() == right.getEmbeddedDouble())
				frame.push(universe.trueObject);
			    else
				frame.push(universe.falseObject);
			}
			else
			    frame.push(universe.falseObject);
		    }
		}
	     );
    
	installInstancePrimitive
	    (new Primitive("<", universe)
		{
		    public void invoke(Frame frame, final Interpreter interpreter)
		    {
			Object rightObj = frame.pop();
			Integer left = (Integer) frame.pop();
			
			// Check second parameter type:
			if (rightObj instanceof BigInteger) {
			    // Second operand was BigInteger
			    resendAsBigInteger("<", left, (BigInteger)rightObj);
			} else if(rightObj instanceof Double) {
                resendAsDouble("<", left, (Double)rightObj);
            } else {
			    // Do operation:
			    Integer right = (Integer)rightObj;
			    
			    if (left.getEmbeddedInteger() < right.getEmbeddedInteger())
				frame.push(universe.trueObject);
			    else
				frame.push(universe.falseObject);
			}
		    }
		}
	     );
    }
}
