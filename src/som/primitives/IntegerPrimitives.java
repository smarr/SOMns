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

public class IntegerPrimitives extends Primitives 
{   
    void pushLongResult(Frame frame, long result)
    {
	// Check with integer bounds and push:
	if (result > java.lang.Integer.MAX_VALUE || result < java.lang.Integer.MIN_VALUE)
	    frame.push(Universe.newBigInteger(result));
	else
	    frame.push(Universe.newInteger((int)result));
    }
    
    void resendAsBigInteger(java.lang.String operator, Integer left, BigInteger right)
    {
	// Construct left value as BigInteger:
	BigInteger leftBigInteger = Universe.newBigInteger((long)left.getEmbeddedInteger());
	
	// Resend message:
	Object[] operands = new Object[1];
	operands[0] = right;
	
	leftBigInteger.send(operator, operands);
    }
    
    void resendAsDouble(java.lang.String operator, Integer left, Double right) {
        Double leftDouble = Universe.newDouble((double)left.getEmbeddedInteger());
        Object[] operands = new Object[] { right };
        leftDouble.send(operator, operands);
    }
        
    public void installPrimitives() 
    {
	installInstancePrimitive
	    (new Primitive("asString")
		{
		    public void invoke(Frame frame)
		    {
			Integer self = (Integer) frame.pop();
			frame.push(Universe.newString(java.lang.Integer.toString(self.getEmbeddedInteger())));
		    }
		}
	     );
	
	installInstancePrimitive(
			new Primitive("sqrt") {
				public void invoke(Frame frame) {
					Integer self = (Integer) frame.pop();
					frame.push(Universe.newDouble(Math.sqrt((double) self.getEmbeddedInteger())));
				}
			}
		);
	
	installInstancePrimitive(
			new Primitive("atRandom") {
				public void invoke(Frame frame) {
					Integer self = (Integer) frame.pop();
					frame.push(Universe.newInteger((int) ((double) self.getEmbeddedInteger() * Math.random())));
				}
			}
		);
    
	installInstancePrimitive
	    (new Primitive("+")
		{
		    public void invoke(Frame frame)
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
	    (new Primitive("-")
		{
		    public void invoke(Frame frame)
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
	    (new Primitive("*")
		{
		    public void invoke(Frame frame)
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
	    (new Primitive("//")
		{
		    public void invoke(Frame frame)
		    {
			/*
			Integer op1 = (Integer) frame.pop();
			Integer op2 = (Integer) frame.pop();
			frame.push(Universe.new_double((double)op2.get_embedded_integer() / (double)op1.get_embedded_integer()));
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
			    frame.push(Universe.newDouble(result));
			}
		    }
		}
	     );
	
	installInstancePrimitive
	    (new Primitive("/")
		{
		    public void invoke(Frame frame)
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
	    (new Primitive("%")
		{
		    public void invoke(Frame frame)
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
	    (new Primitive("&")
		{
		    public void invoke(Frame frame)
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
	    (new Primitive("=")
		{
		    public void invoke(Frame frame)
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
				frame.push(Universe.trueObject);
			    else
				frame.push(Universe.falseObject);
			}
			else if (rightObj instanceof Double) {
			    // Second operand was Integer:
			    Double right = (Double)rightObj;
			    
			    if (left.getEmbeddedInteger() == right.getEmbeddedDouble())
				frame.push(Universe.trueObject);
			    else
				frame.push(Universe.falseObject);
			}
			else
			    frame.push(Universe.falseObject);
		    }
		}
	     );
    
	installInstancePrimitive
	    (new Primitive("<")
		{
		    public void invoke(Frame frame)
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
				frame.push(Universe.trueObject);
			    else
				frame.push(Universe.falseObject);
			}
		    }
		}
	     );
    }
}
