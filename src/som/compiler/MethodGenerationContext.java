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

package som.compiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import som.compiler.Variable.Argument;
import som.compiler.Variable.Local;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.FieldNode.FieldReadNode;
import som.interpreter.nodes.FieldNode.FieldWriteNode;
import som.interpreter.nodes.GlobalNode.GlobalReadNode;
import som.primitives.Primitives;
import som.vm.Universe;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.impl.DefaultSourceSection;

public class MethodGenerationContext {

  // SOM
  private ClassGenerationContext     holderGenc;
  private MethodGenerationContext    outerGenc;
  private boolean                    blockMethod;
  private SSymbol                    signature;
  private boolean                    primitive;

  private final LinkedHashMap<String, Argument> arguments = new LinkedHashMap<String, Argument>();
  private final LinkedHashMap<String, Local>    locals    = new LinkedHashMap<String, Local>();

  // Truffle
  private final FrameDescriptor frameDescriptor;

  public MethodGenerationContext() {
    frameDescriptor = new FrameDescriptor();
  }

  public void setHolder(final ClassGenerationContext cgenc) {
    holderGenc = cgenc;
  }

  public boolean isPrimitive() {
    return primitive;
  }

  public SMethod assemblePrimitive(final Universe universe) {
    return Primitives.getEmptyPrimitive(signature.getString(), universe);
  }

  private void separateLocals(final ArrayList<Local> onlyLocalAccess,
      final ArrayList<Local> nonLocalAccess) {
    for (Local l : locals.values()) {
      if (l.isAccessedOutOfContext()) {
        l.upvalueIndex = nonLocalAccess.size();
        nonLocalAccess.add(l);
      } else {
        onlyLocalAccess.add(l);
      }
    }
  }

  public SMethod assemble(final Universe universe, final ExpressionNode expressions) {
    ArrayList<Local> onlyLocalAccess = new ArrayList<>(locals.size());
    ArrayList<Local> nonLocalAccess  = new ArrayList<>(locals.size());
    separateLocals(onlyLocalAccess, nonLocalAccess);

    FrameSlot[] localSlots = new FrameSlot[onlyLocalAccess.size()];

    for (int i = 0; i < onlyLocalAccess.size(); i++) {
      localSlots[i] = onlyLocalAccess.get(i).slot;
    }

    som.interpreter.Method truffleMethod =
        new som.interpreter.Method(expressions, arguments.size(),
            nonLocalAccess.size(), localSlots, frameDescriptor, universe);

    assignSourceSectionToMethod(expressions, truffleMethod);

    SMethod meth = universe.newMethod(signature, truffleMethod,
        frameDescriptor, false);

    // return the method - the holder field is to be set later on!
    return meth;
  }

  private void assignSourceSectionToMethod(final ExpressionNode expressions,
      final som.interpreter.Method truffleMethod) {
    SourceSection ssBody   = expressions.getSourceSection();
    SourceSection ssMethod = new DefaultSourceSection(ssBody.getSource(),
        holderGenc.getName().getString() + ">>" + signature.toString(),
        ssBody.getStartLine(), ssBody.getStartColumn(),
        ssBody.getCharIndex(), ssBody.getCharLength());

    truffleMethod.assignSourceSection(ssMethod);
  }

  public void setPrimitive(final boolean prim) {
    primitive = prim;
  }

  public void setSignature(final SSymbol sig) {
    signature = sig;
  }

  private Argument addArgument(final String arg) {
    Argument argument = new Argument(arg, arguments.size());
    arguments.put(arg, argument);
    return argument;
  }

  public void addArgumentIfAbsent(final String arg) {
    if (arguments.containsKey(arg)) {
      return;
    }

    addArgument(arg);
  }

  public void addLocalIfAbsent(final String local) {
    if (locals.containsKey(local)) {
      return;
    }

    addLocal(local);
  }

  public void addLocal(final String local) {
    Local l = new Local(local, frameDescriptor.addFrameSlot(local));
    locals.put(local, l);
  }

  public boolean isBlockMethod() {
    return blockMethod;
  }

  public void setIsBlockMethod(final boolean isBlock) {
    blockMethod = isBlock;
  }

  public ClassGenerationContext getHolder() {
    return holderGenc;
  }

  public void setOuter(final MethodGenerationContext mgenc) {
    outerGenc = mgenc;
  }

  public int getSelfContextLevel() {
    int level = 0;
    MethodGenerationContext ctx = outerGenc;
    while (ctx != null) {
      ctx = ctx.getOuter();
      level++;
    }
    return level;
  }

  public int getContextLevel(final String varName) {
    if (locals.containsKey(varName) || arguments.containsKey(varName)) {
      return 0;
    }

    if (outerGenc != null) {
      return 1 + outerGenc.getContextLevel(varName);
    }

    return 0;
  }

  protected Variable getVariable(final String varName) {
    if (locals.containsKey(varName)) {
      return locals.get(varName);
    }

    if (arguments.containsKey(varName)) {
      return arguments.get(varName);
    }

    if (outerGenc != null) {
      return outerGenc.getVariable(varName);
    }
    return null;
  }

  protected Local getLocal(final String varName) {
    if (locals.containsKey(varName)) {
      return locals.get(varName);
    }

    if (outerGenc != null) {
      return outerGenc.getLocal(varName);
    }
    return null;
  }

  public FieldReadNode getObjectFieldRead(final SSymbol fieldName) {
    if (!holderGenc.hasField(fieldName)) {
      return null;
    }

    return new FieldReadNode(holderGenc.getFieldIndex(fieldName),
        getSelfContextLevel());
  }

  public GlobalReadNode getGlobalRead(final SSymbol varName,
      final Universe universe) {
    return new GlobalReadNode(varName, universe);
  }

  public FieldWriteNode getObjectFieldWrite(final SSymbol fieldName,
      final ExpressionNode exp) {
    if (!holderGenc.hasField(fieldName)) {
      return null;
    }
    return new FieldWriteNode(holderGenc.getFieldIndex(fieldName),
        getSelfContextLevel(), exp);
  }

  /**
   * @return number of explicit arguments,
   *         i.e., excluding the implicit 'self' argument
   */
  public int getNumberOfArguments() {
    return arguments.size();
  }

  public MethodGenerationContext getOuter() {
    return outerGenc;
  }

  public SSymbol getSignature() {
    return signature;
  }

  public FrameDescriptor getFrameDescriptor() {
    return frameDescriptor;
  }

  @Override
  public String toString() {
    return "MethodGenC(" + holderGenc.getName().getString() + ">>" + signature.toString() + ")";
  }
}
