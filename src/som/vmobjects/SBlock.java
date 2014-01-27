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

import som.primitives.BlockPrimsFactory.ValueMorePrimFactory;
import som.primitives.BlockPrimsFactory.ValueNonePrimFactory;
import som.primitives.BlockPrimsFactory.ValueOnePrimFactory;
import som.primitives.BlockPrimsFactory.ValueTwoPrimFactory;
import som.primitives.Primitives;
import som.vm.Universe;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.MaterializedFrame;

public class SBlock extends SAbstractObject {

  public SBlock(final SMethod blockMethod, final MaterializedFrame context,
      final FrameSlot outerSelfSlot) {
    this.method   = blockMethod;
    this.context  = context;
    this.outerSelfSlot = outerSelfSlot;
  }

  public SMethod getMethod() {
    // Get the method of this block
    return method;
  }

  public MaterializedFrame getContext() {
    return CompilerDirectives.unsafeFrameCast(context);
  }

  public Object getOuterSelf() {
    return FrameUtil.getObjectSafe(getContext(), outerSelfSlot);
  }

  public static SMethod getEvaluationPrimitive(final int numberOfArguments,
      final Universe universe, final SClass rcvrClass) {
    SSymbol sig = universe.symbolFor(computeSignatureString(numberOfArguments));

    if (numberOfArguments == 1) {
      return Primitives.constructPrimitive(sig,
          ValueNonePrimFactory.getInstance(), universe, rcvrClass);
    } else if (numberOfArguments == 2) {
      return Primitives.constructPrimitive(sig,
          ValueOnePrimFactory.getInstance(), universe, rcvrClass);
    } else if (numberOfArguments == 3) {
      return Primitives.constructPrimitive(sig,
          ValueTwoPrimFactory.getInstance(), universe, rcvrClass);
    } else {
      return Primitives.constructPrimitive(sig,
          ValueMorePrimFactory.getInstance(), universe, rcvrClass);
    }
  }

  private static String computeSignatureString(final int numberOfArguments) {
    // Compute the signature string
    String signatureString = "value";
    if (numberOfArguments > 1) { signatureString += ":"; }

    // Add extra value: selector elements if necessary
    for (int i = 2; i < numberOfArguments; i++) {
      signatureString += "with:";
    }
    return signatureString;
  }

  @Override
  public SClass getSOMClass(final Universe universe) {
    return universe.getBlockClass(method.getNumberOfArguments());
  }

  private final SMethod           method;
  private final MaterializedFrame context;
  private final FrameSlot         outerSelfSlot;
}
