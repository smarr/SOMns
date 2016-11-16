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

import java.util.concurrent.atomic.AtomicInteger;

import som.VmSettings;
import som.vm.constants.Classes;
import tools.actors.ActorExecutionTrace;

public final class SSymbol extends SAbstractObject {
  private final String string;
  private final int    numberOfSignatureArguments;
  private final short symbolId;
  private static AtomicInteger idGenerator = new AtomicInteger(0);

  public SSymbol(final String value) {
    string = value;
    numberOfSignatureArguments = determineNumberOfSignatureArguments();
    if (VmSettings.ACTOR_TRACING) {
      symbolId = (short) idGenerator.getAndIncrement();
      ActorExecutionTrace.logSymbol(this);
    } else {
      symbolId = 0;
    }
  }

  @Override
  public SClass getSOMClass() {
    assert Classes.symbolClass != null;
    return Classes.symbolClass;
  }

  @Override
  public boolean isValue() {
    return true;
  }

  public String getString() {
    // Get the string associated to this symbol
    return string;
  }

  public short getSymbolId() {
    return symbolId;
  }

  private int determineNumberOfSignatureArguments() {
    // Check for binary signature
    if (isBinarySignature()) {
      return 2;
    } else {
      // Count the colons in the signature string
      int numberOfColons = 0;

      // Iterate through every character in the signature string
      for (char c : string.toCharArray()) {
        if (c == ':') { numberOfColons++; }
      }

      // The number of arguments is equal to the number of colons plus one
      return numberOfColons + 1;
    }
  }

  @Override
  public String toString() {
    return "#" + string;
  }

  /**
   * @return number of arguments, including receiver
   */
  public int getNumberOfSignatureArguments() {
    return numberOfSignatureArguments;
  }

  private boolean isBinarySignature() {
    // Check the individual characters of the string
    for (char c : string.toCharArray()) {
      if (c != '~' && c != '&' && c != '|' && c != '*' && c != '/' && c != '@'
          && c != '+' && c != '-' && c != '=' && c != '>' && c != '<'
          && c != ',' && c != '%' && c != '\\') { return false; }
    }
    return true;
  }
}
