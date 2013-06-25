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

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.FrameSlot;

import som.compiler.SourcecodeCompiler.Source;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.ReturnNode.ReturnNonLocalNode;
import som.interpreter.nodes.SequenceNode;
import som.interpreter.nodes.FieldNode.FieldReadNode;
import som.interpreter.nodes.FieldNode.FieldWriteNode;
import som.interpreter.nodes.GlobalNode.GlobalReadNode;
import som.interpreter.nodes.LiteralNode;
import som.interpreter.nodes.LiteralNode.BlockNode;
import som.interpreter.nodes.MessageNode;
import som.interpreter.nodes.VariableNode.SelfReadNode;
import som.interpreter.nodes.VariableNode.SuperReadNode;
import som.interpreter.nodes.VariableNode.VariableReadNode;
import som.interpreter.nodes.VariableNode.VariableWriteNode;

import som.vm.Universe;

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
    for (Symbol s : new Symbol[] { Not, And, Or, Star, Div, Mod, Plus, Equal,
      More, Less, Comma, At, Per, NONE })
      singleOpSyms.add(s);
    for (Symbol s : new Symbol[] { Or, Comma, Minus, Equal, Not, And, Or, Star,
      Div, Mod, Plus, Equal, More, Less, Comma, At, Per, NONE })
      binaryOpSyms.add(s);
    for (Symbol s : new Symbol[] { Keyword, KeywordSequence })
      keywordSelectorSyms.add(s);
  }
  
  private static class SourceCoordinate {
    public final Source source;
    public final int startLine;
    public final int startColumn;
    public final int charIndex;
    
    public SourceCoordinate(final Source source, final int startLine,
        final int startColumn, final int charIndex) {
      this.source = source;
      this.startLine = startLine;
      this.startColumn = startColumn;
      this.charIndex = charIndex;
    }
  }

  public Parser(Reader reader, final Source source, final Universe universe) {
    this.universe = universe;
    this.source   = source;

    sym = NONE;
    lexer = new Lexer(reader);
    nextSym = NONE;
    GETSYM();
  }
  
  private SourceCoordinate getCoordinate() {
    return new SourceCoordinate(source, lexer.getCurrentLineNumber(),
        lexer.getCurrentColumn(),
        lexer.getNumberOfCharactersRead());
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
      //mgenc.addArgument("self");

      SequenceNode methodBody = method(mgenc);

      if (mgenc.isPrimitive())
        cgenc.addInstanceMethod(mgenc.assemblePrimitive(universe));
      else
        cgenc.addInstanceMethod(mgenc.assemble(universe, methodBody));
    }

    if (accept(Separator)) {
      cgenc.setClassSide(true);
      classFields(cgenc);
      while (sym == Identifier || sym == Keyword || sym == OperatorSequence
          || symIn(binaryOpSyms)) {
        MethodGenerationContext mgenc = new MethodGenerationContext();
        mgenc.setHolder(cgenc);
        mgenc.addArgument("self");

        SequenceNode methodBody = method(mgenc);

        if (mgenc.isPrimitive())
          cgenc.addClassMethod(mgenc.assemblePrimitive(universe));
        else
          cgenc.addClassMethod(mgenc.assemble(universe, methodBody));
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
  
  private void assignSource(ExpressionNode node, SourceCoordinate coord) {
    node.assignSourceSection(new SourceSection(source, "method",
        coord.startLine, coord.startColumn, coord.charIndex,
        lexer.getNumberOfCharactersRead() - coord.charIndex));
  }

  private SequenceNode method(MethodGenerationContext mgenc) {
    SourceCoordinate coord = getCoordinate();
    
    pattern(mgenc);
    expect(Equal);
    if (sym == Primitive) {
      mgenc.setPrimitive(true);
      primitiveBlock();
      return null;
    }
    else {
      SequenceNode seq = methodBlock(mgenc);
      assignSource(seq, coord);
      return seq;
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

  private SequenceNode methodBlock(final MethodGenerationContext mgenc) {
    SourceCoordinate coord = getCoordinate();
    expect(NewTerm);
    SequenceNode sequence = blockContents(mgenc);
    // TODO: test whether we always have the right return value here, removed
    //       code that made sure that self is returned. Had the feeling it was redundant.
    expect(EndTerm);
    assignSource(sequence, coord);
    return sequence;
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

        expressions.add(new SelfReadNode(mgenc.getSelfSlot()));
  private String keyword() {
    String s = new String(text);
    expect(Keyword);

    return s;
  }

  private String argument() {
    return variable();
  }

  private SequenceNode blockContents(final MethodGenerationContext mgenc) {
    SourceCoordinate coord = getCoordinate();
    if (accept(Or)) {
      locals(mgenc);
      expect(Or);
    }
    
    SequenceNode seq = blockBody(mgenc);
    assignSource(seq, coord);
    return seq;
  }

  private void locals(final MethodGenerationContext mgenc) {
    while (sym == Identifier)
      mgenc.addLocalIfAbsent(variable());
  }

  private SequenceNode blockBody(final MethodGenerationContext mgenc) {
    SourceCoordinate coord = getCoordinate();
    List<ExpressionNode> expressions = new ArrayList<ExpressionNode>();
    
    while (true) {
      if (accept(Exit)) {
        expressions.add(result(mgenc));
        
        SequenceNode seq = new SequenceNode(expressions.toArray(new ExpressionNode[0]));
        assignSource(seq, coord);
        return seq;
      }
      else if (sym == EndBlock) {
        SequenceNode seq = new SequenceNode(expressions.toArray(new ExpressionNode[0]));
        assignSource(seq, coord);
        return seq;
      }
      else if (sym == EndTerm) {
        // the end of the method has been found (EndTerm) - make it implicitly
        // return "self"
        expressions.add(new SelfReadNode(mgenc.getSelfSlot(), mgenc.getSelfContextLevel()));
        SequenceNode seq = new SequenceNode(expressions.toArray(new ExpressionNode[0]));
        assignSource(seq, coord);
        return seq;
      }
      
      expressions.add(expression(mgenc));
      accept(Period);
    }
  }

  private ExpressionNode result(MethodGenerationContext mgenc) {
    SourceCoordinate coord = getCoordinate();
    ExpressionNode exp = expression(mgenc);
    
    accept(Period);

    ExpressionNode result;
    if (mgenc.isBlockMethod())
      result = new ReturnNonLocalNode(exp);
    else
      result = exp; // TODO: figure out whether implicit return is sufficient, would think so, don't see why we would need a control-flow exception here.
    
    assignSource(result, coord);
    return result;
  }

  private ExpressionNode expression(final MethodGenerationContext mgenc) {
    SourceCoordinate coord = getCoordinate();
    
    PEEK();
    
    ExpressionNode result;
    if (nextSym == Assign)
      result = assignation(mgenc);
    else
      result = evaluation(mgenc);
    
    assignSource(result, coord);
    return result;
  }

  private ExpressionNode assignation(final MethodGenerationContext mgenc) {
    return assignments(mgenc);
  }

  private ExpressionNode assignments(final MethodGenerationContext mgenc) {
    SourceCoordinate coord = getCoordinate();
    
    if (sym != Identifier)
      throw new RuntimeException("This is every unexpected, assignments should always target variables or fields. But found instead a: " + sym.toString());
    
    String variable = assignment();
      
    PEEK();
      
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
    SourceCoordinate coord = getCoordinate();
    ExpressionNode exp = primary(mgenc);
    if (sym == Identifier || sym == Keyword || sym == OperatorSequence
        || symIn(binaryOpSyms)) {
      exp = messages(mgenc, exp);
    }
    
    assignSource(exp, coord);
    return exp;
  }

  private ExpressionNode primary(final MethodGenerationContext mgenc) {
    SourceCoordinate coord = getCoordinate();
    ExpressionNode result;
    switch (sym) {
      case Identifier: {
        String v = variable();
        result = variableRead(mgenc, v);
        break;
      }
      case NewTerm: {
        result = nestedTerm(mgenc);
        break;
      }
      case NewBlock: {
        MethodGenerationContext bgenc = new MethodGenerationContext();
        bgenc.setIsBlockMethod(true);
        bgenc.setHolder(mgenc.getHolder());
        bgenc.setOuter(mgenc);

        SequenceNode blockBody = nestedBlock(bgenc);

        som.vmobjects.Method blockMethod = bgenc.assemble(universe, blockBody);
        result = new BlockNode(blockMethod, universe);
        break;
      }
      default: {
        result = literal();
        break;
      }
    }
    
    assignSource(result, coord);
    return result;
  }

  private String variable() {
    return identifier();
  }

  private MessageNode messages(final MethodGenerationContext mgenc,
      final ExpressionNode receiver) {
    SourceCoordinate coord = getCoordinate();
    MessageNode msg;
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
    }
    else if (sym == OperatorSequence || symIn(binaryOpSyms)) {
      msg = binaryMessage(mgenc, receiver);
      
      while (sym == OperatorSequence || symIn(binaryOpSyms)) {
        msg = binaryMessage(mgenc, msg);
      }

      if (sym == Keyword) {
        msg = keywordMessage(mgenc, msg);
      }
    }
    else
      msg = keywordMessage(mgenc, receiver);
    
    assignSource(msg, coord);
    return msg;
  }

  private MessageNode unaryMessage(ExpressionNode receiver) {
    SourceCoordinate coord = getCoordinate();
    som.vmobjects.Symbol selector = unarySelector();
    MessageNode msg = new MessageNode(receiver, null, selector, universe);
    assignSource(msg, coord);
    return msg;
  }

  private MessageNode binaryMessage(final MethodGenerationContext mgenc, ExpressionNode receiver) {
    SourceCoordinate coord = getCoordinate();
    som.vmobjects.Symbol msg = binarySelector();
    ExpressionNode operand   = binaryOperand(mgenc);
    
    MessageNode msgNode = new MessageNode(receiver, new ExpressionNode[] { operand }, msg, universe);
    assignSource(msgNode, coord);
    return msgNode;
  }

  private ExpressionNode binaryOperand(final MethodGenerationContext mgenc) {
    SourceCoordinate coord = getCoordinate();
    ExpressionNode operand = primary(mgenc);

    // a binary operand can receive unaryMessages
    // Example: 2 * 3 asString
    //   is evaluated as 2 * (3 asString)
    while (sym == Identifier)
      operand = unaryMessage(operand);
    
    assignSource(operand, coord);
    return operand;
  }

  private MessageNode keywordMessage(final MethodGenerationContext mgenc,
      final ExpressionNode receiver) {
    SourceCoordinate coord = getCoordinate();
    List<ExpressionNode> arguments = new ArrayList<ExpressionNode>();
    StringBuffer         kw        = new StringBuffer();
    
    do {
      kw.append(keyword());
      arguments.add(formula(mgenc));
    }
    while (sym == Keyword);

    som.vmobjects.Symbol msg = universe.symbolFor(kw.toString());

    MessageNode msgNode = new MessageNode(receiver, arguments.toArray(new ExpressionNode[0]), msg, universe);
    assignSource(msgNode, coord);
    return msgNode;
  }

  private ExpressionNode formula(final MethodGenerationContext mgenc) {
    SourceCoordinate coord = getCoordinate();
    ExpressionNode operand = binaryOperand(mgenc);

    while (sym == OperatorSequence || symIn(binaryOpSyms))
      operand = binaryMessage(mgenc, operand);
    
    assignSource(operand, coord);
    return operand;
  }

  private ExpressionNode nestedTerm(MethodGenerationContext mgenc) {
    SourceCoordinate coord = getCoordinate();

    expect(NewTerm);
    ExpressionNode exp = expression(mgenc);
    expect(EndTerm);
    
    assignSource(exp, coord);
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
    
    LiteralNode node = new LiteralNode(lit);
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
    long i = java.lang.Long.parseLong(text);
    expect(Integer);
    return i;
  }

  private LiteralNode literalSymbol() {
    SourceCoordinate coord = getCoordinate();
    
    som.vmobjects.Symbol symb;
    expect(Pound);
    if (sym == STString) {
      String s = string();
      symb = universe.symbolFor(s);
    }
    else
      symb = selector();
    
    LiteralNode lit = new LiteralNode(symb);
    assignSource(lit, coord);
    return lit;
  }

  private LiteralNode literalString() {
    SourceCoordinate coord = getCoordinate();
    String s = string();

    som.vmobjects.String str = universe.newString(s);
    
    LiteralNode lit = new LiteralNode(str);
    assignSource(lit, coord);
    return lit;
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

  private SequenceNode nestedBlock(final MethodGenerationContext mgenc) {
    SourceCoordinate coord = getCoordinate();
    
    // mgenc.addArgumentIfAbsent("$block self");

    expect(NewBlock);
    if (sym == Colon) blockPattern(mgenc);

    // generate Block signature
    String blockSig = "$block method";
    int argSize = mgenc.getNumberOfArguments();
    for (int i = 1; i < argSize; i++)
      blockSig += ":";

    mgenc.setSignature(universe.symbolFor(blockSig));

    SequenceNode expressions = blockContents(mgenc);

    expect(EndBlock);
    
    assignSource(expressions, coord);
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
    if ("self".equals(variableName))
      return new SelfReadNode(mgenc.getSelfSlot(), mgenc.getSelfContextLevel());
    
    if ("super".equals(variableName))
      return new SuperReadNode(mgenc.getSelfSlot(), mgenc.getSelfContextLevel());
    
    // now look up first local variables, or method arguments
    FrameSlot frameSlot = mgenc.getFrameSlot(variableName);
    
    if (frameSlot != null)
      return new VariableReadNode(frameSlot, mgenc.getFrameSlotContextLevel(variableName));
    
    // then object fields
    som.vmobjects.Symbol varName = universe.symbolFor(variableName); 
    FieldReadNode fieldRead = mgenc.getObjectFieldRead(varName);
    
    if (fieldRead != null)
      return fieldRead;
    
    // and finally assume it is a global
    GlobalReadNode globalRead = mgenc.getGlobalRead(varName, universe);
    return globalRead;
  }
  
  private ExpressionNode variableWrite(final MethodGenerationContext mgenc,
      final String variableName,
      final ExpressionNode exp) {
    FrameSlot frameSlot = mgenc.getFrameSlot(variableName);
    
    if (frameSlot != null)
      return new VariableWriteNode(frameSlot, exp);
    
    som.vmobjects.Symbol fieldName = universe.symbolFor(variableName);
    FieldWriteNode fieldWrite = mgenc.getObjectFieldWrite(fieldName, exp);
    
    if (fieldWrite != null)
      return fieldWrite;
    else
      throw new RuntimeException("Neither a variable nor a field found in current scope that is named " + variableName + ".");
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
