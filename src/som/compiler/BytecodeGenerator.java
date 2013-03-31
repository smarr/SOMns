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

import static som.interpreter.Bytecodes.dup;
import static som.interpreter.Bytecodes.pop;
import static som.interpreter.Bytecodes.pop_argument;
import static som.interpreter.Bytecodes.pop_field;
import static som.interpreter.Bytecodes.pop_local;
import static som.interpreter.Bytecodes.push_argument;
import static som.interpreter.Bytecodes.push_block;
import static som.interpreter.Bytecodes.push_constant;
import static som.interpreter.Bytecodes.push_field;
import static som.interpreter.Bytecodes.push_global;
import static som.interpreter.Bytecodes.push_local;
import static som.interpreter.Bytecodes.return_local;
import static som.interpreter.Bytecodes.return_non_local;
import static som.interpreter.Bytecodes.send;
import static som.interpreter.Bytecodes.super_send;
import som.vmobjects.Method;
import som.vmobjects.Symbol;

public class BytecodeGenerator {
    
    public void emitPOP(MethodGenerationContext mgenc) {
        EMIT1(mgenc, pop);
    }

    public void emitPUSHARGUMENT(MethodGenerationContext mgenc, byte idx, byte ctx) {
        EMIT3(mgenc, push_argument, idx, ctx);
    }

    public void emitRETURNLOCAL(MethodGenerationContext mgenc) {
        EMIT1(mgenc, return_local);
    }

    public void emitRETURNNONLOCAL(MethodGenerationContext mgenc) {
        EMIT1(mgenc, return_non_local);
    }

    public void emitDUP(MethodGenerationContext mgenc) {
        EMIT1(mgenc, dup);
    }

    public void emitPUSHBLOCK(MethodGenerationContext mgenc, Method blockMethod) {
        EMIT2(mgenc, push_block, mgenc.findLiteralIndex(blockMethod));
    }

    public void emitPUSHLOCAL(MethodGenerationContext mgenc, byte idx, byte ctx) {
        EMIT3(mgenc, push_local, idx, ctx);
    }

    public void emitPUSHFIELD(MethodGenerationContext mgenc, Symbol fieldName) {
        EMIT2(mgenc, push_field, mgenc.findLiteralIndex(fieldName));
    }

    public void emitPUSHGLOBAL(MethodGenerationContext mgenc, Symbol global) {
        EMIT2(mgenc, push_global, mgenc.findLiteralIndex(global));
    }

    public void emitPOPARGUMENT(MethodGenerationContext mgenc, byte idx, byte ctx) {
        EMIT3(mgenc, pop_argument, idx, ctx);
    }

    public void emitPOPLOCAL(MethodGenerationContext mgenc, byte idx, byte ctx) {
        EMIT3(mgenc, pop_local, idx, ctx);
    }

    public void emitPOPFIELD(MethodGenerationContext mgenc, Symbol fieldName) {
        EMIT2(mgenc, pop_field, mgenc.findLiteralIndex(fieldName));
    }

    public void emitSUPERSEND(MethodGenerationContext mgenc, Symbol msg) {
        EMIT2(mgenc, super_send, mgenc.findLiteralIndex(msg));
    }

    public void emitSEND(MethodGenerationContext mgenc, Symbol msg) {
        EMIT2(mgenc, send, mgenc.findLiteralIndex(msg));
    }

    public void emitPUSHCONSTANT(MethodGenerationContext mgenc, som.vmobjects.Object lit) {
        EMIT2(mgenc, push_constant, mgenc.findLiteralIndex(lit));
    }
    
    private void EMIT1(MethodGenerationContext mgenc, byte code) {
        mgenc.addBytecode(code);
    }

    private void EMIT2(MethodGenerationContext mgenc, byte code, byte idx) {
        mgenc.addBytecode(code);
        mgenc.addBytecode(idx);
    }

    private void EMIT3(MethodGenerationContext mgenc, byte code, byte idx, byte ctx) {
        mgenc.addBytecode(code);
        mgenc.addBytecode(idx);
        mgenc.addBytecode(ctx);
    }

}
