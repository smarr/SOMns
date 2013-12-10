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

import som.compiler.SourcecodeCompiler.Source;
import som.interpreter.nodes.AbstractMessageNode;
import som.interpreter.nodes.BinaryMessageNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.FieldNode.FieldReadNode;
import som.interpreter.nodes.FieldNode.FieldWriteNode;
import som.interpreter.nodes.GlobalNode.GlobalReadNode;
import som.interpreter.nodes.NodeFactory;
import som.interpreter.nodes.ReturnNonLocalNode;
import som.interpreter.nodes.SequenceNode;
import som.interpreter.nodes.UnaryMessageNode;
import som.interpreter.nodes.VariableNode.SelfReadNode;
import som.interpreter.nodes.VariableNode.SuperReadNode;
import som.interpreter.nodes.VariableNode.VariableReadNode;
import som.interpreter.nodes.VariableWriteNode;
import som.interpreter.nodes.literals.BigIntegerLiteralNodeFactory;
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.literals.IntegerLiteralNodeFactory;
import som.interpreter.nodes.literals.LiteralNode;
import som.interpreter.nodes.literals.StringLiteralNodeFactory;
import som.interpreter.nodes.literals.SymbolLiteralNode;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.impl.DefaultSourceSection;

public class Parser {

  private final Universe            universe;
  private final Lexer               lexer;
  private final Source              source;

  private Symbol                    sym;
  private String                    text;
  private Symbol                    nextSym;

  private static final List<Symbol> singleOpSyms        = new ArrayList<Symbol>();
  private static final List<Symbol> binaryOpSyms        = new ArrayList<Symbol>();
  private static final List<Symbol> keywordSelectorSyms = new ArrayList<Symbol>();

  static {
    for (Symbol s : new Symbol[] {Not, And, Or, Star, Div, Mod, Plus, Equal,
        More, Less, Comma, At, Per, NONE}) {
      singleOpSyms.add(s);
    }
    for (Symbol s : new Symbol[] {Or, Comma, Minus, Equal, Not, And, Or, Star,
        Div, Mod, Plus, Equal, More, Less, Comma, At, Per, NONE}) {
      binaryOpSyms.add(s);
    }
    for (Symbol s : new Symbol[] {Keyword, KeywordSequence}) {
      keywordSelectorSyms.add(s);
    }
  }

  private static class SourceCoordinate {
    public final int startLine;
    public final int startColumn;
    public final int charIndex;

    public SourceCoordinate(final int startLine,
        final int startColumn, final int charIndex) {
      this.startLine = startLine;
      this.startColumn = startColumn;
      this.charIndex = charIndex;
    }
  }

  public Parser(final Reader reader, final Source source, final Universe universe) {
    this.universe = universe;
    this.source   = source;

    sym = NONE;
    lexer = new Lexer(reader);
    nextSym = NONE;
    getSymbolFromLexer();
  }

  private SourceCoordinate getCoordinate() {
    return new SourceCoordinate(lexer.getCurrentLineNumber(),
        lexer.getCurrentColumn(),
        lexer.getNumberOfCharactersRead());
  }

  @SlowPath
  public void classdef(final ClassGenerationContext cgenc) {
    cgenc.setName(universe.symbolFor(text));
    expect(Identifier);
    expect(Equal);

    SSymbol superName;
    if (sym == Identifier) {
      superName = universe.symbolFor(text);
      accept(Identifier);
    } else {
      superName = universe.symbolFor("Object");
    }
    cgenc.setSuperName(superName);

    // Load the super class, if it is not nil (break the dependency cycle)
    if (!superName.getString().equals("nil")) {
      SClass superClass = universe.loadClass(superName);
      cgenc.setInstanceFieldsOfSuper(superClass.getInstanceFields());
      cgenc.setClassFieldsOfSuper(superClass.getSOMClass(universe).getInstanceFields());
    }

    expect(NewTerm);
    instanceFields(cgenc);
    while (sym == Identifier || sym == Keyword || sym == OperatorSequence
        || symIn(binaryOpSyms)) {
      MethodGenerationContext mgenc = new MethodGenerationContext();
      mgenc.setHolder(cgenc);

      ExpressionNode methodBody = method(mgenc);

      if (mgenc.isPrimitive()) {
        cgenc.addInstanceMethod(mgenc.assemblePrimitive(universe));
      } else {
        cgenc.addInstanceMethod(mgenc.assemble(universe, methodBody));
      }
    }

    if (accept(Separator)) {
      cgenc.setClassSide(true);
      classFields(cgenc);
      while (sym == Identifier || sym == Keyword || sym == OperatorSequence
          || symIn(binaryOpSyms)) {
        MethodGenerationContext mgenc = new MethodGenerationContext();
        mgenc.setHolder(cgenc);

        ExpressionNode methodBody = method(mgenc);

        if (mgenc.isPrimitive()) {
          cgenc.addClassMethod(mgenc.assemblePrimitive(universe));
        } else {
          cgenc.addClassMethod(mgenc.assemble(universe, methodBody));
        }
      }
    }
    expect(EndTerm);
  }

  private boolean symIn(final List<Symbol> ss) {
    return ss.contains(sym);
  }

  private boolean accept(final Symbol s) {
    if (sym == s) {
      getSymbolFromLexer();
      return true;
    }
    return false;
  }

  private boolean acceptOneOf(final List<Symbol> ss) {
    if (symIn(ss)) {
      getSymbolFromLexer();
      return true;
    }
    return false;
  }

  private boolean expect(final Symbol s) {
    if (accept(s)) { return true; }
    StringBuffer err = new StringBuffer("Error: unexpected symbol in line "
        + lexer.getCurrentLineNumber() + ". Expected " + s.toString()
        + ", but found " + sym.toString());
    if (printableSymbol()) { err.append(" (" + text + ")"); }
    err.append(": " + lexer.getRawBuffer());
    throw new IllegalStateException(err.toString());
  }

  private boolean expectOneOf(final List<Symbol> ss) {
    if (acceptOneOf(ss)) { return true; }
    StringBuffer err = new StringBuffer("Error: unexpected symbol in line "
        + lexer.getCurrentLineNumber() + ". Expected one of ");
    for (Symbol s : ss) {
      err.append(s.toString() + ", ");
    }
    err.append("but found " + sym.toString());
    if (printableSymbol()) { err.append(" (" + text + ")"); }
    err.append(": " + lexer.getRawBuffer());
    throw new IllegalStateException(err.toString());
  }

  private void instanceFields(final ClassGenerationContext cgenc) {
    if (accept(Or)) {
      while (sym == Identifier) {
        String var = variable();
        cgenc.addInstanceField(universe.symbolFor(var));
      }
      expect(Or);
    }
  }

  private void classFields(final ClassGenerationContext cgenc) {
    if (accept(Or)) {
      while (sym == Identifier) {
        String var = variable();
        cgenc.addClassField(universe.symbolFor(var));
      }
      expect(Or);
    }
  }

  private void assignSource(final ExpressionNode node, final SourceCoordinate coord) {
    node.assignSourceSection(new DefaultSourceSection(source, "method",
        coord.startLine, coord.startColumn, coord.charIndex,
        lexer.getNumberOfCharactersRead() - coord.charIndex));
  }

  private ExpressionNode method(final MethodGenerationContext mgenc) {
    pattern(mgenc);
    expect(Equal);
    if (sym == Primitive) {
      mgenc.setPrimitive(true);
      primitiveBlock();
      return null;
    } else {
      return methodBlock(mgenc);
    }
  }

  private void primitiveBlock() {
    expect(Primitive);
  }

  private void pattern(final MethodGenerationContext mgenc) {
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

  private void unaryPattern(final MethodGenerationContext mgenc) {
    mgenc.setSignature(unarySelector());
  }

  private void binaryPattern(final MethodGenerationContext mgenc) {
    mgenc.setSignature(binarySelector());
    mgenc.addArgumentIfAbsent(argument());
  }

  private void keywordPattern(final MethodGenerationContext mgenc) {
    StringBuffer kw = new StringBuffer();
    do {
      kw.append(keyword());
      mgenc.addArgumentIfAbsent(argument());
    }
    while (sym == Keyword);

    mgenc.setSignature(universe.symbolFor(kw.toString()));
  }

  private ExpressionNode methodBlock(final MethodGenerationContext mgenc) {
    expect(NewTerm);
    ExpressionNode sequence = blockContents(mgenc);
    expect(EndTerm);
    return sequence;
  }

  private SSymbol unarySelector() {
    return universe.symbolFor(identifier());
  }

  private SSymbol binarySelector() {
    String s = new String(text);

    // Checkstyle: stop
    if (accept(Or)) {
    } else if (accept(Comma)) {
    } else if (accept(Minus)) {
    } else if (accept(Equal)) {
    } else if (acceptOneOf(singleOpSyms)) {
    } else if (accept(OperatorSequence)) {
    } else { expect(NONE); }
    // Checkstyle: resume

    return universe.symbolFor(s);
  }

  private String identifier() {
    String s = new String(text);
    boolean isPrimitive = accept(Primitive);
    if (!isPrimitive) {
      expect(Identifier);
    }
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

  private ExpressionNode blockContents(final MethodGenerationContext mgenc) {
    if (accept(Or)) {
      locals(mgenc);
      expect(Or);
    }
    return blockBody(mgenc);
  }

  private void locals(final MethodGenerationContext mgenc) {
    while (sym == Identifier) {
      mgenc.addLocalIfAbsent(variable());
    }
  }

  private ExpressionNode blockBody(final MethodGenerationContext mgenc) {
    SourceCoordinate coord = getCoordinate();
    List<ExpressionNode> expressions = new ArrayList<ExpressionNode>();

    while (true) {
      if (accept(Exit)) {
        expressions.add(result(mgenc));
        return createSequenceNode(coord, expressions);
      } else if (sym == EndBlock) {
        return createSequenceNode(coord, expressions);
      } else if (sym == EndTerm) {
        // the end of the method has been found (EndTerm) - make it implicitly
        // return "self"
        SelfReadNode self = new SelfReadNode(mgenc.getSelfSlot(), mgenc.getSelfContextLevel());
        SourceCoordinate selfCoord = getCoordinate();
        assignSource(self, selfCoord);
        expressions.add(self);
        return createSequenceNode(coord, expressions);
      }

      expressions.add(expression(mgenc));
      accept(Period);
    }
  }

  private ExpressionNode createSequenceNode(final SourceCoordinate coord,
      final List<ExpressionNode> expressions) {
    if (expressions.size() == 1) {
      return expressions.get(0);
    }

    SequenceNode seq = new SequenceNode(expressions.toArray(new ExpressionNode[0]));
    assignSource(seq, coord);
    return seq;
  }

  private ExpressionNode result(final MethodGenerationContext mgenc) {
    ExpressionNode exp = expression(mgenc);

    SourceCoordinate coord = getCoordinate();
    accept(Period);

    if (mgenc.isBlockMethod()) {
      ExpressionNode result = new ReturnNonLocalNode(exp,
          mgenc.getSelfContextLevel(), universe);
      assignSource(result, coord);
      return result;
    } else {
      return exp;
    }
  }

  private ExpressionNode expression(final MethodGenerationContext mgenc) {
    peekForNextSymbolFromLexer();

    if (nextSym == Assign) {
      return assignation(mgenc);
    } else {
      return evaluation(mgenc);
    }
  }

  private ExpressionNode assignation(final MethodGenerationContext mgenc) {
    return assignments(mgenc);
  }

  private ExpressionNode assignments(final MethodGenerationContext mgenc) {
    SourceCoordinate coord = getCoordinate();

    if (sym != Identifier) {
      throw new RuntimeException("This is very unexpected, "
          + "assignments should always target variables or fields. "
          + "But found instead a: " + sym.toString());
    }
    String variable = assignment();

    peekForNextSymbolFromLexer();

    ExpressionNode value;
    if (nextSym == Assign) {
      value = assignments(mgenc);
    } else {
      value = evaluation(mgenc);
    }

    ExpressionNode exp = variableWrite(mgenc, variable, value);
    assignSource(exp, coord);
    return exp;
  }

  private String assignment() {
    String v = variable();
    expect(Assign);
    return v;
  }

  private ExpressionNode evaluation(final MethodGenerationContext mgenc) {
    ExpressionNode exp = primary(mgenc);
    if (sym == Identifier || sym == Keyword || sym == OperatorSequence
        || symIn(binaryOpSyms)) {
      exp = messages(mgenc, exp);
    }
    return exp;
  }

  private ExpressionNode primary(final MethodGenerationContext mgenc) {
    switch (sym) {
      case Identifier: {
        SourceCoordinate coord = getCoordinate();
        String v = variable();
        ExpressionNode varRead = variableRead(mgenc, v);
        assignSource(varRead, coord);
        return varRead;
      }
      case NewTerm: {
        return nestedTerm(mgenc);
      }
      case NewBlock: {
        SourceCoordinate coord = getCoordinate();
        MethodGenerationContext bgenc = new MethodGenerationContext();
        bgenc.setIsBlockMethod(true);
        bgenc.setHolder(mgenc.getHolder());
        bgenc.setOuter(mgenc);

        ExpressionNode blockBody = nestedBlock(bgenc);

        SMethod blockMethod = bgenc.assemble(universe, blockBody);
        ExpressionNode result = new BlockNode(blockMethod, universe);
        assignSource(result, coord);
        return result;
      }
      default: {
        return literal();
      }
    }
  }

  private String variable() {
    return identifier();
  }

  private AbstractMessageNode messages(final MethodGenerationContext mgenc,
      final ExpressionNode receiver) {
    AbstractMessageNode msg;
    if (sym == Identifier) {
      msg = unaryMessage(receiver);

      while (sym == Identifier) {
        msg = unaryMessage(msg);
      }

      while (sym == OperatorSequence || symIn(binaryOpSyms)) {
        msg = binaryMessage(mgenc, msg);
      }

      if (sym == Keyword) {
        msg = keywordMessage(mgenc, msg);
      }
    } else if (sym == OperatorSequence || symIn(binaryOpSyms)) {
      msg = binaryMessage(mgenc, receiver);

      while (sym == OperatorSequence || symIn(binaryOpSyms)) {
        msg = binaryMessage(mgenc, msg);
      }

      if (sym == Keyword) {
        msg = keywordMessage(mgenc, msg);
      }
    } else {
      msg = keywordMessage(mgenc, receiver);
    }
    return msg;
  }

  private UnaryMessageNode unaryMessage(final ExpressionNode receiver) {
    SourceCoordinate coord = getCoordinate();
    SSymbol selector = unarySelector();
    UnaryMessageNode msg = NodeFactory.createUnaryMessageNode(selector, universe, receiver);
    assignSource(msg, coord);
    return msg;
  }

  private BinaryMessageNode binaryMessage(final MethodGenerationContext mgenc,
      final ExpressionNode receiver) {
    SourceCoordinate coord = getCoordinate();
    SSymbol msg = binarySelector();
    ExpressionNode operand = binaryOperand(mgenc);

    BinaryMessageNode msgNode = NodeFactory.createBinaryMessageNode(msg, universe, receiver, operand);
    assignSource(msgNode, coord);
    return msgNode;
  }

  private ExpressionNode binaryOperand(final MethodGenerationContext mgenc) {
    ExpressionNode operand = primary(mgenc);

    // a binary operand can receive unaryMessages
    // Example: 2 * 3 asString
    //   is evaluated as 2 * (3 asString)
    while (sym == Identifier) {
      operand = unaryMessage(operand);
    }
    return operand;
  }

  private AbstractMessageNode keywordMessage(final MethodGenerationContext mgenc,
      final ExpressionNode receiver) {
    SourceCoordinate coord = getCoordinate();
    List<ExpressionNode> arguments = new ArrayList<ExpressionNode>();
    StringBuffer         kw        = new StringBuffer();

    do {
      kw.append(keyword());
      arguments.add(formula(mgenc));
    }
    while (sym == Keyword);

    SSymbol msg = universe.symbolFor(kw.toString());

    AbstractMessageNode msgNode = NodeFactory.createMessageNode(msg, universe,
        receiver, arguments.toArray(new ExpressionNode[0]));
    assignSource(msgNode, coord);
    return msgNode;
  }

  private ExpressionNode formula(final MethodGenerationContext mgenc) {
    ExpressionNode operand = binaryOperand(mgenc);

    while (sym == OperatorSequence || symIn(binaryOpSyms)) {
      operand = binaryMessage(mgenc, operand);
    }
    return operand;
  }

  private ExpressionNode nestedTerm(final MethodGenerationContext mgenc) {
    expect(NewTerm);
    ExpressionNode exp = expression(mgenc);
    expect(EndTerm);
    return exp;
  }

  private LiteralNode literal() {
    switch (sym) {
      case Pound:     return literalSymbol();
      case STString:  return literalString();
      default:        return literalNumber();
    }
  }

  private LiteralNode literalNumber() {
    SourceCoordinate coord = getCoordinate();
    long val;
    if (sym == Minus) {
      val = negativeDecimal();
    } else {
      val = literalDecimal();
    }

    LiteralNode node;
    if (val < java.lang.Integer.MIN_VALUE || val > java.lang.Integer.MAX_VALUE) {
      node = BigIntegerLiteralNodeFactory.create(java.math.BigInteger.valueOf(val));
    } else {
      node = IntegerLiteralNodeFactory.create((int) val);
    }

    assignSource(node, coord);
    return node;
  }

  private long literalDecimal() {
    return literalInteger();
  }

  private long negativeDecimal() {
    expect(Minus);
    return -literalInteger();
  }

  private long literalInteger() {
    long i = Long.parseLong(text);
    expect(Integer);
    return i;
  }

  private LiteralNode literalSymbol() {
    SourceCoordinate coord = getCoordinate();

    SSymbol symb;
    expect(Pound);
    if (sym == STString) {
      String s = string();
      symb = universe.symbolFor(s);
    } else {
      symb = selector();
    }

    LiteralNode lit = new SymbolLiteralNode(symb);
    assignSource(lit, coord);
    return lit;
  }

  private LiteralNode literalString() {
    SourceCoordinate coord = getCoordinate();
    String s = string();

    LiteralNode node = StringLiteralNodeFactory.create(s);
    assignSource(node, coord);
    return node;
  }

  private SSymbol selector() {
    if (sym == OperatorSequence || symIn(singleOpSyms)) {
      return binarySelector();
    } else if (sym == Keyword || sym == KeywordSequence) {
      return keywordSelector();
    } else {
      return unarySelector();
    }
  }

  private SSymbol keywordSelector() {
    String s = new String(text);
    expectOneOf(keywordSelectorSyms);
    SSymbol symb = universe.symbolFor(s);
    return symb;
  }

  private String string() {
    String s = new String(text);
    expect(STString);
    return s;
  }

  private ExpressionNode nestedBlock(final MethodGenerationContext mgenc) {
    expect(NewBlock);
    if (sym == Colon) { blockPattern(mgenc); }

    // generate Block signature
    String blockSig = "$block method";
    int argSize = mgenc.getNumberOfArguments();
    for (int i = 0; i < argSize; i++) {
      blockSig += ":";
    }

    mgenc.setSignature(universe.symbolFor(blockSig));

    ExpressionNode expressions = blockContents(mgenc);

    expect(EndBlock);

    return expressions;
  }

  private void blockPattern(final MethodGenerationContext mgenc) {
    blockArguments(mgenc);
    expect(Or);
  }

  private void blockArguments(final MethodGenerationContext mgenc) {
    do {
      expect(Colon);
      mgenc.addArgumentIfAbsent(argument());
    }
    while (sym == Colon);
  }

  private ExpressionNode variableRead(final MethodGenerationContext mgenc,
                                      final String variableName) {
    // first handle the keywords/reserved names
    if ("self".equals(variableName)) {
      return new SelfReadNode(mgenc.getSelfSlot(), mgenc.getSelfContextLevel());
    }

    if ("super".equals(variableName)) {
      return new SuperReadNode(mgenc.getSelfSlot(), mgenc.getSelfContextLevel());
    }

    // now look up first local variables, or method arguments
    FrameSlot frameSlot = mgenc.getFrameSlot(variableName);

    if (frameSlot != null) {
      return new VariableReadNode(frameSlot,
          mgenc.getFrameSlotContextLevel(variableName));
    }

    // then object fields
    SSymbol varName = universe.symbolFor(variableName);
    FieldReadNode fieldRead = mgenc.getObjectFieldRead(varName);

    if (fieldRead != null) {
      return fieldRead;
    }

    // and finally assume it is a global
    GlobalReadNode globalRead = mgenc.getGlobalRead(varName, universe);
    return globalRead;
  }

  private ExpressionNode variableWrite(final MethodGenerationContext mgenc,
      final String variableName,
      final ExpressionNode exp) {
    FrameSlot frameSlot = mgenc.getFrameSlot(variableName);

    if (frameSlot != null) {
      return new VariableWriteNode(frameSlot,
          mgenc.getFrameSlotContextLevel(variableName), exp);
    }

    SSymbol fieldName = universe.symbolFor(variableName);
    FieldWriteNode fieldWrite = mgenc.getObjectFieldWrite(fieldName, exp);

    if (fieldWrite != null) {
      return fieldWrite;
    } else {
      throw new RuntimeException("Neither a variable nor a field found "
          + "in current scope that is named " + variableName + ".");
    }
  }

  private void getSymbolFromLexer() {
    sym  = lexer.getSym();
    text = lexer.getText();
  }

  private void peekForNextSymbolFromLexer() {
    nextSym = lexer.peek();
  }

  private boolean printableSymbol() {
    return sym == Integer || sym.compareTo(STString) >= 0;
  }

}
