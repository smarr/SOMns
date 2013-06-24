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

package som.interpreter;

/** REMOVED FOR TRUFFLE
public class Bytecodes {

  // Bytecodes used by the simple object machine
  public static final byte               halt             = 0;
  public static final byte               dup              = 1;
  public static final byte               push_local       = 2;
  public static final byte               push_argument    = 3;
  public static final byte               push_field       = 4;
  public static final byte               push_block       = 5;
  public static final byte               push_constant    = 6;
  public static final byte               push_global      = 7;
  public static final byte               pop              = 8;
  public static final byte               pop_local        = 9;
  public static final byte               pop_argument     = 10;
  public static final byte               pop_field        = 11;
  public static final byte               send             = 12;
  public static final byte               super_send       = 13;
  public static final byte               return_local     = 14;
  public static final byte               return_non_local = 15;

  public static final java.lang.String[] bytecodeNames    = new java.lang.String[] {
      "HALT            ", "DUP             ", "PUSH_LOCAL      ",
      "PUSH_ARGUMENT   ", "PUSH_FIELD      ", "PUSH_BLOCK      ",
      "PUSH_CONSTANT   ", "PUSH_GLOBAL     ", "POP             ",
      "POP_LOCAL       ", "POP_ARGUMENT    ", "POP_FIELD       ",
      "SEND            ", "SUPER_SEND      ", "RETURN_LOCAL    ",
      "RETURN_NON_LOCAL"                                 };

  private static final byte              numBytecodes     = 16;

  public static int getBytecodeLength(byte bytecode) {
    // Return the length of the given bytecode
    return bytecodeLength[bytecode];
  }

  // Static array holding lengths of each bytecode
  private static int[] bytecodeLength = new int[numBytecodes];

  static {
    // set up the lengths of the "native" bytecodes
    bytecodeLength[halt] = 1;
    bytecodeLength[dup] = 1;
    bytecodeLength[push_local] = 3;
    bytecodeLength[push_argument] = 3;
    bytecodeLength[push_field] = 2;
    bytecodeLength[push_block] = 2;
    bytecodeLength[push_constant] = 2;
    bytecodeLength[push_global] = 2;
    bytecodeLength[pop] = 1;
    bytecodeLength[pop_local] = 3;
    bytecodeLength[pop_argument] = 3;
    bytecodeLength[pop_field] = 2;
    bytecodeLength[send] = 2;
    bytecodeLength[super_send] = 2;
    bytecodeLength[return_local] = 1;
    bytecodeLength[return_non_local] = 1;
  }

}
*/