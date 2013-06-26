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

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.FieldNode.FieldReadNode;
import som.interpreter.nodes.FieldNode.FieldWriteNode;
import som.interpreter.nodes.GlobalNode.GlobalReadNode;
import som.interpreter.nodes.SequenceNode;
import som.vm.Universe;
import som.vmobjects.Invokable;
import som.vmobjects.Method;
import som.vmobjects.Primitive;
import som.vmobjects.Symbol;

/** REMOVED FOR TRUFFLE import static som.interpreter.Bytecodes.*; */

public class MethodGenerationContext {

  // SOM
  private ClassGenerationContext     holderGenc;
  private MethodGenerationContext    outerGenc;
  private boolean                    blockMethod;
  private som.vmobjects.Symbol       signature;
  private boolean                    primitive;
  
  private final List<String>         arguments = new ArrayList<String>();
  private final List<String>         locals    = new ArrayList<String>();
  
  /** REMOVED FOR TRUFFLE
  private Vector<Byte>               bytecode  = new Vector<Byte>();
  private List<som.vmobjects.Object> literals  = new ArrayList<som.vmobjects.Object>();
  */
  
  // Truffle
  private final FrameDescriptor frameDescriptor = new FrameDescriptor();
  private final FrameSlot       selfSlot;
  
  public MethodGenerationContext() {
    selfSlot = frameDescriptor.addFrameSlot("self", FrameSlotKind.Object);
  }
  
  public FrameSlot getSelfSlot() {
    return selfSlot;
  }

  public void setHolder(ClassGenerationContext cgenc) {
    holderGenc = cgenc;
  }

  public boolean isPrimitive() {
    return primitive;
  }

  public Invokable assemblePrimitive(final Universe universe) {
    return Primitive.getEmptyPrimitive(signature.getString(), universe);
  }

  public Method assemble(final Universe universe, final SequenceNode expressions) {
    FrameSlot[] argSlots = new FrameSlot[arguments.size()];
    
    for (int i = 0; i < arguments.size(); i++) {
      argSlots[i] = frameDescriptor.findFrameSlot(arguments.get(i));
    }
    
    som.interpreter.nodes.Method truffleMethod = new som.interpreter.nodes.Method(expressions, selfSlot, argSlots);

    Method meth = universe.newMethod(signature, truffleMethod, frameDescriptor);
    
    // return the method - the holder field is to be set later on!
    return meth;
  }

  /** REMOVED FOR TRUFFLE
  private int computeStackDepth() {
    int depth = 0;
    int maxDepth = 0;
    int i = 0;

    while (i < bytecode.size()) {
      switch (bytecode.elementAt(i)) {
        case halt:
          i++;
          break;
        case dup:
          depth++;
          i++;
          break;
        case push_local:
        case push_argument:
          depth++;
          i += 3;
          break;
        case push_field:
        case push_block:
        case push_constant:
        case push_global:
          depth++;
          i += 2;
          break;
        case pop:
          depth--;
          i++;
          break;
        case pop_local:
        case pop_argument:
          depth--;
          i += 3;
          break;
        case pop_field:
          depth--;
          i += 2;
          break;
        case send:
        case super_send: {
          // these are special: they need to look at the number of
          // arguments (extractable from the signature)
          som.vmobjects.Symbol sig = (som.vmobjects.Symbol) literals
              .get(bytecode.elementAt(i + 1));

          depth -= sig.getNumberOfSignatureArguments();

          depth++; // return value
          i += 2;
          break;
        }
        case return_local:
        case return_non_local:
          i++;
          break;
        default:
          throw new IllegalStateException("Illegal bytecode "
              + bytecode.elementAt(i));
      }

      if (depth > maxDepth) maxDepth = depth;
    }

    return maxDepth;
  } */

  public void setPrimitive(boolean prim) {
    primitive = prim;
  }

  public void setSignature(Symbol sig) {
    signature = sig;
  }

  public FrameSlot addArgument(String arg) {
    arguments.add(arg);
    return frameDescriptor.addFrameSlot(arg, FrameSlotKind.Object);
  }
  
  public void addArgumentIfAbsent(String arg) {
    if (arguments.contains(arg))
      return;
    
    addArgument(arg);
  }
  
  public void addLocalIfAbsent(String local) {
    if (locals.contains(local))
      return;
    
    addLocal(local);
  }

  public void addLocal(String local) {
    frameDescriptor.addFrameSlot(local);
    locals.add(local);
  }

  public boolean isBlockMethod() {
    return blockMethod;
  }

  /** REMOVED FOR TRUFFLE
  public boolean addLiteralIfAbsent(som.vmobjects.Object lit) {
    if (literals.contains(lit)) return false;
    literals.add(lit);
    return true;
  } */

  public void setIsBlockMethod(boolean isBlock) {
    blockMethod = isBlock;
  }

  public ClassGenerationContext getHolder() {
    return holderGenc;
  }

  public void setOuter(MethodGenerationContext mgenc) {
    outerGenc = mgenc;
  }

  /** REMOVED FOR TRUFFLE
  public void addLiteral(som.vmobjects.Object lit) {
    literals.add(lit);
  } */
  
  public int getSelfContextLevel() {
    int level = 0;
    MethodGenerationContext ctx = outerGenc;
    while (ctx != null) {
      ctx = ctx.getOuter();
      level++;
    }
    return level;
  }
  
  public FrameSlot getOuterSelfSlot() {
    if (outerGenc == null)
      return selfSlot;
    else
      return outerGenc.getOuterSelfSlot();    
  }

  public int getFrameSlotContextLevel(final String varName) {
    if (locals.contains(varName) || arguments.contains(varName)) {
      return 0;
    }
    
    if (outerGenc != null) {
      return 1 + outerGenc.getFrameSlotContextLevel(varName);
    }
    
    return 0;
  }
  
  public FrameSlot getFrameSlot(final String varName) {
    if (locals.contains(varName) || arguments.contains(varName)) {
      return frameDescriptor.findFrameSlot(varName);
    }
    
    FrameSlot slot = null;
    if (outerGenc != null) {
      slot = outerGenc.getFrameSlot(varName);
    }
    
    return slot;
  }
  
  public FieldReadNode getObjectFieldRead(Symbol fieldName) {
    if (!holderGenc.hasField(fieldName))
      return null;
    
    return new FieldReadNode(fieldName, getOuterSelfSlot(), getSelfContextLevel());
  }
  
  public GlobalReadNode getGlobalRead(final Symbol varName,
      final Universe universe) {
    return new GlobalReadNode(varName, getOuterSelfSlot(), universe);
  }
  
  public FieldWriteNode getObjectFieldWrite(final Symbol fieldName,
      final ExpressionNode exp) {
    if (!holderGenc.hasField(fieldName))
      return null;
    
    return new FieldWriteNode(fieldName, getOuterSelfSlot(), getSelfContextLevel(), exp);
  }

  /**
   * @return number of explicit arguments, 
   *         i.e., excluding the implicit 'self' argument
   */
  public int getNumberOfArguments() {
    return arguments.size();
  }

  /** REMOVED FOR TRUFFLE
  public void addBytecode(byte code) {
    bytecode.add(code);
  }

  public byte findLiteralIndex(som.vmobjects.Object lit) {
    return (byte) literals.indexOf(lit);
  } */

  public MethodGenerationContext getOuter() {
    return outerGenc;
  }

  public som.vmobjects.Symbol getSignature() {
    return signature;
  }

}
