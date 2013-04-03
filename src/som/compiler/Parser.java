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

import static som.compiler.Symbol.And;
import static som.compiler.Symbol.Assign;
import static som.compiler.Symbol.At;
import static som.compiler.Symbol.Colon;
import static som.compiler.Symbol.Comma;
import static som.compiler.Symbol.Div;
import static som.compiler.Symbol.EndBlock;
import static som.compiler.Symbol.EndTerm;
import static som.compiler.Symbol.Equal;
import static som.compiler.Symbol.Exit;
import static som.compiler.Symbol.Identifier;
import static som.compiler.Symbol.Integer;
import static som.compiler.Symbol.Keyword;
import static som.compiler.Symbol.KeywordSequence;
import static som.compiler.Symbol.Less;
import static som.compiler.Symbol.Minus;
import static som.compiler.Symbol.Mod;
import static som.compiler.Symbol.More;
import static som.compiler.Symbol.NONE;
import static som.compiler.Symbol.NewBlock;
import static som.compiler.Symbol.NewTerm;
import static som.compiler.Symbol.Not;
import static som.compiler.Symbol.OperatorSequence;
import static som.compiler.Symbol.Or;
import static som.compiler.Symbol.Per;
import static som.compiler.Symbol.Period;
import static som.compiler.Symbol.Plus;
import static som.compiler.Symbol.Pound;
import static som.compiler.Symbol.Primitive;
import static som.compiler.Symbol.STString;
import static som.compiler.Symbol.Separator;
import static som.compiler.Symbol.Star;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import som.vm.Universe;

public class Parser {

  private final Universe            universe;

  private final Lexer               lexer;
  private final BytecodeGenerator   bcGen;

  private Symbol                    sym;
  private String                    text;
  private Symbol                    nextSym;

  private static final List<Symbol> singleOpSyms        = new ArrayList<Symbol>();
  private static final List<Symbol> binaryOpSyms        = new ArrayList<Symbol>();
  private static final List<Symbol> keywordSelectorSyms = new ArrayList<Symbol>();

  static {
    for (Symbol s : new Symbol[] { Not, And, Or, Star, Div, Mod, Plus, Equal,
      More, Less, Comma, At, Per, NONE })
      singleOpSyms.add(s);
    for (Symbol s : new Symbol[] { Or, Comma, Minus, Equal, Not, And, Or, Star,
      Div, Mod, Plus, Equal, More, Less, Comma, At, Per, NONE })
      binaryOpSyms.add(s);
    for (Symbol s : new Symbol[] { Keyword, KeywordSequence })
      keywordSelectorSyms.add(s);
  }

  public Parser(Reader reader, final Universe universe) {
    this.universe = universe;

    sym = NONE;
    lexer = new Lexer(reader);
    bcGen = new BytecodeGenerator();
    nextSym = NONE;
    GETSYM();
  }

  public void classdef(ClassGenerationContext cgenc) {
    cgenc.setName(universe.symbolFor(text));
    expect(Identifier);
    expect(Equal);

    if (sym == Identifier) {
      cgenc.setSuperName(universe.symbolFor(text));
      accept(Identifier);
    }
    else
      cgenc.setSuperName(universe.symbolFor("Object"));

    expect(NewTerm);
    instanceFields(cgenc);
    while (sym == Identifier || sym == Keyword || sym == OperatorSequence
        || symIn(binaryOpSyms)) {
      MethodGenerationContext mgenc = new MethodGenerationContext();
      mgenc.setHolder(cgenc);
      mgenc.addArgument("self");

      method(mgenc);

      if (mgenc.isPrimitive())
        cgenc.addInstanceMethod(mgenc.assemblePrimitive(universe));
      else
        cgenc.addInstanceMethod(mgenc.assemble(universe));
    }

    if (accept(Separator)) {
      cgenc.setClassSide(true);
      classFields(cgenc);
      while (sym == Identifier || sym == Keyword || sym == OperatorSequence
          || symIn(binaryOpSyms)) {
        MethodGenerationContext mgenc = new MethodGenerationContext();
        mgenc.setHolder(cgenc);
        mgenc.addArgument("self");

        method(mgenc);

        if (mgenc.isPrimitive())
          cgenc.addClassMethod(mgenc.assemblePrimitive(universe));
        else
          cgenc.addClassMethod(mgenc.assemble(universe));
      }
    }
    expect(EndTerm);
  }

  private boolean symIn(List<Symbol> ss) {
    return ss.contains(sym);
  }

  private boolean accept(Symbol s) {
    if (sym == s) {
      GETSYM();
      return true;
    }
    return false;
  }

  private boolean acceptOneOf(List<Symbol> ss) {
    if (symIn(ss)) {
      GETSYM();
      return true;
    }
    return false;
  }

  private boolean expect(Symbol s) {
    if (accept(s)) return true;
    StringBuffer err = new StringBuffer("Error: unexpected symbol in line "
        + lexer.getCurrentLineNumber() + ". Expected " + s.toString()
        + ", but found " + sym.toString());
    if (printableSymbol()) err.append(" (" + text + ")");
    err.append(": " + lexer.getRawBuffer());
    throw new IllegalStateException(err.toString());
  }

  private boolean expectOneOf(List<Symbol> ss) {
    if (acceptOneOf(ss)) return true;
    StringBuffer err = new StringBuffer("Error: unexpected symbol in line "
        + lexer.getCurrentLineNumber() + ". Expected one of ");
    for (Symbol s : ss)
      err.append(s.toString() + ", ");
    err.append("but found " + sym.toString());
    if (printableSymbol()) err.append(" (" + text + ")");
    err.append(": " + lexer.getRawBuffer());
    throw new IllegalStateException(err.toString());
  }

  private void instanceFields(ClassGenerationContext cgenc) {
    if (accept(Or)) {
      while (sym == Identifier) {
        String var = variable();
        cgenc.addInstanceField(universe.symbolFor(var));
      }
      expect(Or);
    }
  }

  private void classFields(ClassGenerationContext cgenc) {
    if (accept(Or)) {
      while (sym == Identifier) {
        String var = variable();
        cgenc.addClassField(universe.symbolFor(var));
      }
      expect(Or);
    }
  }

  private void method(MethodGenerationContext mgenc) {
    pattern(mgenc);
    expect(Equal);
    if (sym == Primitive) {
      mgenc.setPrimitive(true);
      primitiveBlock();
    }
    else
      methodBlock(mgenc);
  }

  private void primitiveBlock() {
    expect(Primitive);
  }

  private void pattern(MethodGenerationContext mgenc) {
    switch (sym) {
      case Identifier:
        unaryPattern(mgenc);
        break;
      case Keyword:
        keywordPattern(mgenc);
        break;
      default:
        binaryPattern(mgenc);
        break;
    }
  }

  private void unaryPattern(MethodGenerationContext mgenc) {
    mgenc.setSignature(unarySelector());
  }

  private void binaryPattern(MethodGenerationContext mgenc) {
    mgenc.setSignature(binarySelector());
    mgenc.addArgumentIfAbsent(argument());
  }

  private void keywordPattern(MethodGenerationContext mgenc) {
    StringBuffer kw = new StringBuffer();
    do {
      kw.append(keyword());
      mgenc.addArgumentIfAbsent(argument());
    }
    while (sym == Keyword);

    mgenc.setSignature(universe.symbolFor(kw.toString()));
  }

  private void methodBlock(MethodGenerationContext mgenc) {
    expect(NewTerm);
    blockContents(mgenc);
    // if no return has been generated so far, we can be sure there was no .
    // terminating the last expression, so the last expression's value must
    // be
    // popped off the stack and a ^self be generated
    if (!mgenc.isFinished()) {
      bcGen.emitPOP(mgenc);
      bcGen.emitPUSHARGUMENT(mgenc, (byte) 0, (byte) 0);
      bcGen.emitRETURNLOCAL(mgenc);
      mgenc.setFinished();
    }

    expect(EndTerm);
  }

  private som.vmobjects.Symbol unarySelector() {
    return universe.symbolFor(identifier());
  }

  private som.vmobjects.Symbol binarySelector() {
    String s = new String(text);

    if (accept(Or))
      ;
    else if (accept(Comma))
      ;
    else if (accept(Minus))
      ;
    else if (accept(Equal))
      ;
    else if (acceptOneOf(singleOpSyms))
      ;
    else if (accept(OperatorSequence))
      ;
    else
      expect(NONE);

    return universe.symbolFor(s);
  }

  private String identifier() {
    String s = new String(text);
    if (accept(Primitive))
      ; // text is set
    else
      expect(Identifier);

    return s;
  }

  private String keyword() {
    String s = new String(text);
    expect(Keyword);

    return s;
  }

  private String argument() {
    return variable();
  }

  private void blockContents(MethodGenerationContext mgenc) {
    if (accept(Or)) {
      locals(mgenc);
      expect(Or);
    }
    blockBody(mgenc, false);
  }

  private void locals(MethodGenerationContext mgenc) {
    while (sym == Identifier)
      mgenc.addLocalIfAbsent(variable());
  }

  private void blockBody(MethodGenerationContext mgenc, boolean seenPeriod) {
    if (accept(Exit))
      result(mgenc);
    else if (sym == EndBlock) {
      if (seenPeriod) {
        // a POP has been generated which must be elided (blocks always
        // return the value of the last expression, regardless of
        // whether it
        // was terminated with a . or not)
        mgenc.removeLastBytecode();
      }
      bcGen.emitRETURNLOCAL(mgenc);
      mgenc.setFinished();
    }
    else if (sym == EndTerm) {
      // it does not matter whether a period has been seen, as the end of
      // the
      // method has been found (EndTerm) - so it is safe to emit a "return
      // self"
      bcGen.emitPUSHARGUMENT(mgenc, (byte) 0, (byte) 0);
      bcGen.emitRETURNLOCAL(mgenc);
      mgenc.setFinished();
    }
    else {
      expression(mgenc);
      if (accept(Period)) {
        bcGen.emitPOP(mgenc);
        blockBody(mgenc, true);
      }
    }
  }

  private void result(MethodGenerationContext mgenc) {
    expression(mgenc);

    if (mgenc.isBlockMethod())
      bcGen.emitRETURNNONLOCAL(mgenc);
    else
      bcGen.emitRETURNLOCAL(mgenc);

    mgenc.setFinished(true);
    accept(Period);
  }

  private void expression(MethodGenerationContext mgenc) {
    PEEK();
    if (nextSym == Assign)
      assignation(mgenc);
    else
      evaluation(mgenc);
  }

  private void assignation(MethodGenerationContext mgenc) {
    List<String> l = new ArrayList<String>();

    assignments(mgenc, l);
    evaluation(mgenc);

    for (int i = 1; i <= l.size(); i++)
      bcGen.emitDUP(mgenc);
    for (String s : l)
      genPopVariable(mgenc, s);
  }

  private void assignments(MethodGenerationContext mgenc, List<String> l) {
    if (sym == Identifier) {
      l.add(assignment(mgenc));
      PEEK();
      if (nextSym == Assign) assignments(mgenc, l);
    }
  }

  private String assignment(MethodGenerationContext mgenc) {
    String v = variable();
    som.vmobjects.Symbol var = universe.symbolFor(v);
    mgenc.addLiteralIfAbsent(var);

    expect(Assign);

    return v;
  }

  private void evaluation(MethodGenerationContext mgenc) {
    // single: superSend
    Single<Boolean> si = new Single<Boolean>(false);

    primary(mgenc, si);
    if (sym == Identifier || sym == Keyword || sym == OperatorSequence
        || symIn(binaryOpSyms)) {
      messages(mgenc, si);
    }
  }

  private void primary(MethodGenerationContext mgenc, Single<Boolean> superSend) {
    superSend.set(false);
    switch (sym) {
      case Identifier: {
        String v = variable();
        if (v.equals("super")) {
          superSend.set(true);
          // sends to super push self as the receiver
          v = "self";
        }

        genPushVariable(mgenc, v);
        break;
      }
      case NewTerm:
        nestedTerm(mgenc);
        break;
      case NewBlock: {
        MethodGenerationContext bgenc = new MethodGenerationContext();
        bgenc.setIsBlockMethod(true);
        bgenc.setHolder(mgenc.getHolder());
        bgenc.setOuter(mgenc);

        nestedBlock(bgenc);

        som.vmobjects.Method blockMethod = bgenc.assemble(universe);
        mgenc.addLiteral(blockMethod);
        bcGen.emitPUSHBLOCK(mgenc, blockMethod);
        break;
      }
      default:
        literal(mgenc);
        break;
    }
  }

  private String variable() {
    return identifier();
  }

  private void messages(MethodGenerationContext mgenc, Single<Boolean> superSend) {
    if (sym == Identifier) {
      do {
        // only the first message in a sequence can be a super send
        unaryMessage(mgenc, superSend);
        superSend.set(false);
      }
      while (sym == Identifier);

      while (sym == OperatorSequence || symIn(binaryOpSyms)) {
        binaryMessage(mgenc, new Single<Boolean>(false));
      }

      if (sym == Keyword) {
        keywordMessage(mgenc, new Single<Boolean>(false));
      }
    }
    else if (sym == OperatorSequence || symIn(binaryOpSyms)) {
      do {
        // only the first message in a sequence can be a super send
        binaryMessage(mgenc, superSend);
        superSend.set(false);
      }
      while (sym == OperatorSequence || symIn(binaryOpSyms));

      if (sym == Keyword) {
        keywordMessage(mgenc, new Single<Boolean>(false));
      }
    }
    else
      keywordMessage(mgenc, superSend);
  }

  private void unaryMessage(MethodGenerationContext mgenc,
      Single<Boolean> superSend) {
    som.vmobjects.Symbol msg = unarySelector();
    mgenc.addLiteralIfAbsent(msg);

    if (superSend.get())
      bcGen.emitSUPERSEND(mgenc, msg);
    else
      bcGen.emitSEND(mgenc, msg);
  }

  private void binaryMessage(MethodGenerationContext mgenc,
      Single<Boolean> superSend) {
    som.vmobjects.Symbol msg = binarySelector();
    mgenc.addLiteralIfAbsent(msg);

    binaryOperand(mgenc, new Single<Boolean>(false));

    if (superSend.get())
      bcGen.emitSUPERSEND(mgenc, msg);
    else
      bcGen.emitSEND(mgenc, msg);
  }

  private void binaryOperand(MethodGenerationContext mgenc,
      Single<Boolean> superSend) {
    primary(mgenc, superSend);

    while (sym == Identifier)
      unaryMessage(mgenc, superSend);
  }

  private void keywordMessage(MethodGenerationContext mgenc,
      Single<Boolean> superSend) {
    StringBuffer kw = new StringBuffer();
    do {
      kw.append(keyword());
      formula(mgenc);
    }
    while (sym == Keyword);

    som.vmobjects.Symbol msg = universe.symbolFor(kw.toString());

    mgenc.addLiteralIfAbsent(msg);

    if (superSend.get())
      bcGen.emitSUPERSEND(mgenc, msg);
    else
      bcGen.emitSEND(mgenc, msg);
  }

  private void formula(MethodGenerationContext mgenc) {
    Single<Boolean> superSend = new Single<Boolean>(false);
    binaryOperand(mgenc, superSend);

    // only the first message in a sequence can be a super send
    if (sym == OperatorSequence || symIn(binaryOpSyms))
      binaryMessage(mgenc, superSend);
    while (sym == OperatorSequence || symIn(binaryOpSyms))
      binaryMessage(mgenc, new Single<Boolean>(false));
  }

  private void nestedTerm(MethodGenerationContext mgenc) {
    expect(NewTerm);
    expression(mgenc);
    expect(EndTerm);
  }

  private void literal(MethodGenerationContext mgenc) {
    switch (sym) {
      case Pound:
        literalSymbol(mgenc);
        break;
      case STString:
        literalString(mgenc);
        break;
      default:
        literalNumber(mgenc);
        break;
    }
  }

  private void literalNumber(MethodGenerationContext mgenc) {
    long val;
    if (sym == Minus)
      val = negativeDecimal();
    else
      val = literalDecimal();

    som.vmobjects.Object lit;
    if (val < java.lang.Integer.MIN_VALUE || val > java.lang.Integer.MAX_VALUE) {
      lit = universe.newBigInteger(val);
    }
    else {
      lit = universe.newInteger((int)val);
    }
    mgenc.addLiteralIfAbsent(lit);
    bcGen.emitPUSHCONSTANT(mgenc, lit);
  }

  private long literalDecimal() {
    return literalInteger();
  }

  private long negativeDecimal() {
    expect(Minus);
    return -literalInteger();
  }

  private long literalInteger() {
    long i = java.lang.Long.parseLong(text);
    expect(Integer);
    return i;
  }

  private void literalSymbol(MethodGenerationContext mgenc) {
    som.vmobjects.Symbol symb;
    expect(Pound);
    if (sym == STString) {
      String s = string();
      symb = universe.symbolFor(s);
    }
    else
      symb = selector();
    mgenc.addLiteralIfAbsent(symb);
    bcGen.emitPUSHCONSTANT(mgenc, symb);
  }

  private void literalString(MethodGenerationContext mgenc) {
    String s = string();

    som.vmobjects.String str = universe.newString(s);
    mgenc.addLiteralIfAbsent(str);

    bcGen.emitPUSHCONSTANT(mgenc, str);
  }

  private som.vmobjects.Symbol selector() {
    if (sym == OperatorSequence || symIn(singleOpSyms))
      return binarySelector();
    else if (sym == Keyword || sym == KeywordSequence)
      return keywordSelector();
    else
      return unarySelector();
  }

  private som.vmobjects.Symbol keywordSelector() {
    String s = new String(text);
    expectOneOf(keywordSelectorSyms);
    som.vmobjects.Symbol symb = universe.symbolFor(s);
    return symb;
  }

  private String string() {
    String s = new String(text);
    expect(STString);
    return s;
  }

  private void nestedBlock(MethodGenerationContext mgenc) {
    mgenc.addArgumentIfAbsent("$block self");

    expect(NewBlock);
    if (sym == Colon) blockPattern(mgenc);

    // generate Block signature
    String blockSig = "$block method";
    int argSize = mgenc.getNumberOfArguments();
    for (int i = 1; i < argSize; i++)
      blockSig += ":";

    mgenc.setSignature(universe.symbolFor(blockSig));

    blockContents(mgenc);

    // if no return has been generated, we can be sure that the last
    // expression
    // in the block was not terminated by ., and can generate a return
    if (!mgenc.isFinished()) {
      bcGen.emitRETURNLOCAL(mgenc);
      mgenc.setFinished(true);
    }

    expect(EndBlock);
  }

  private void blockPattern(MethodGenerationContext mgenc) {
    blockArguments(mgenc);
    expect(Or);
  }

  private void blockArguments(MethodGenerationContext mgenc) {
    do {
      expect(Colon);
      mgenc.addArgumentIfAbsent(argument());
    }
    while (sym == Colon);
  }

  private void genPushVariable(MethodGenerationContext mgenc, String var) {
    // The purpose of this function is to find out whether the variable to
    // be
    // pushed on the stack is a local variable, argument, or object field.
    // This
    // is done by examining all available lexical contexts, starting with
    // the
    // innermost (i.e., the one represented by mgenc).

    // triplet: index, context, isArgument
    Triplet<Byte, Byte, Boolean> tri = new Triplet<Byte, Byte, Boolean>(
        (byte) 0, (byte) 0, false);

    if (mgenc.findVar(var, tri)) {
      if (tri.getZ())
        bcGen.emitPUSHARGUMENT(mgenc, tri.getX(), tri.getY());
      else
        bcGen.emitPUSHLOCAL(mgenc, tri.getX(), tri.getY());
    }
    else if (mgenc.findField(var)) {
      som.vmobjects.Symbol fieldName = universe.symbolFor(var);
      mgenc.addLiteralIfAbsent(fieldName);
      bcGen.emitPUSHFIELD(mgenc, fieldName);
    }
    else {
      som.vmobjects.Symbol global = universe.symbolFor(var);
      mgenc.addLiteralIfAbsent(global);
      bcGen.emitPUSHGLOBAL(mgenc, global);
    }
  }

  private void genPopVariable(MethodGenerationContext mgenc, String var) {
    // The purpose of this function is to find out whether the variable to
    // be
    // popped off the stack is a local variable, argument, or object field.
    // This
    // is done by examining all available lexical contexts, starting with
    // the
    // innermost (i.e., the one represented by mgenc).

    // triplet: index, context, isArgument
    Triplet<Byte, Byte, Boolean> tri = new Triplet<Byte, Byte, Boolean>(
        (byte) 0, (byte) 0, false);

    if (mgenc.findVar(var, tri)) {
      if (tri.getZ())
        bcGen.emitPOPARGUMENT(mgenc, tri.getX(), tri.getY());
      else
        bcGen.emitPOPLOCAL(mgenc, tri.getX(), tri.getY());
    }
    else
      bcGen.emitPOPFIELD(mgenc, universe.symbolFor(var));
  }

  private void GETSYM() {
    sym = lexer.getSym();
    text = lexer.getText();
  }

  private void PEEK() {
    nextSym = lexer.peek();
  }

  private boolean printableSymbol() {
    return sym == Integer || sym.compareTo(STString) >= 0;
  }

}
