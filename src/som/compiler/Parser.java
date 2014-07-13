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
import static som.compiler.Symbol.Double;
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
import static som.interpreter.SNodeFactory.createBlockNode;
import static som.interpreter.SNodeFactory.createGlobalRead;
import static som.interpreter.SNodeFactory.createMessageSend;
import static som.interpreter.SNodeFactory.createSequence;

import java.io.Reader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import som.compiler.Variable.Local;
import som.interpreter.Invokable;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.FieldNode.AbstractFieldReadNode;
import som.interpreter.nodes.FieldNode.AbstractFieldWriteNode;
import som.interpreter.nodes.literals.BigIntegerLiteralNode;
import som.interpreter.nodes.literals.DoubleLiteralNode;
import som.interpreter.nodes.literals.IntegerLiteralNode;
import som.interpreter.nodes.literals.StringLiteralNode;
import som.interpreter.nodes.literals.SymbolLiteralNode;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.Source;
import com.oracle.truffle.api.SourceSection;

public final class Parser {

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

  private static ExpressionTuple tuple(final ExpressionNode en, final ExpressionNode un) {
    return new ExpressionTuple(en, un);
  }

  private static final class ExpressionTuple {
    public final ExpressionNode en;
    public final ExpressionNode un;

    public ExpressionTuple(final ExpressionNode en, final ExpressionNode un) {
      this.en = en;
      this.un = un;

      assert en == null ||  en.nodeExecutesEnforced();
      assert un == null || !un.nodeExecutesEnforced();
    }
  }

  @Override
  public String toString() {
    return "Parser(" + source.getName() + ", " + this.getCoordinate().toString() + ")";
  }

  private static final class SourceCoordinate {
    public final int startLine;
    public final int startColumn;
    public final int charIndex;

    public SourceCoordinate(final int startLine,
        final int startColumn, final int charIndex) {
      this.startLine = startLine;
      this.startColumn = startColumn;
      this.charIndex = charIndex;
    }

    @Override
    public String toString() {
      return "SrcCoord(line: " + startLine + ", col: " + startColumn + ")";
    }
  }

  public static class ParseError extends Exception {
    private static final long serialVersionUID = 425390202979033628L;
    private final String message;
    private final SourceCoordinate sourceCoordinate;
    private final String text;
    private final String rawBuffer;
    private final String fileName;
    private final Symbol expected;
    private final Symbol found;

    ParseError(final String message, final Symbol expected, final Parser parser) {
      this.message = message;
      this.sourceCoordinate = parser.getCoordinate();
      this.text             = parser.text;
      this.rawBuffer        = parser.lexer.getRawBuffer();
      this.fileName         = parser.source.getName();
      this.expected         = expected;
      this.found            = parser.sym;
    }

    protected String expectedSymbolAsString() {
      return expected.toString();
    }

    @Override
    public String toString() {
      String msg = "%(file)s:%(line)d:%(column)d: error: " + message;
      String foundStr;
      if (Parser.printableSymbol(found)) {
        foundStr = found + " (" + text + ")";
      } else {
        foundStr = found.toString();
      }
      msg += ": " + rawBuffer;
      String expectedStr = expectedSymbolAsString();

      msg = msg.replace("%(file)s",     fileName);
      msg = msg.replace("%(line)d",     java.lang.Integer.toString(sourceCoordinate.startLine));
      msg = msg.replace("%(column)d",   java.lang.Integer.toString(sourceCoordinate.startColumn));
      msg = msg.replace("%(expected)s", expectedStr);
      msg = msg.replace("%(found)s",    foundStr);
      return msg;
    }
  }

  public static class ParseErrorWithSymbolList extends ParseError {
    private static final long serialVersionUID = 561313162441723955L;
    private final List<Symbol> expectedSymbols;

    ParseErrorWithSymbolList(final String message, final List<Symbol> expected,
        final Parser parser) {
      super(message, null, parser);
      this.expectedSymbols = expected;
    }

    @Override
    protected String expectedSymbolAsString() {
        StringBuilder sb = new StringBuilder();
        String deliminator = "";

        for (Symbol s : expectedSymbols) {
            sb.append(deliminator);
            sb.append(s);
            deliminator = ", ";
        }
        return sb.toString();
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
  public void classdef(final ClassGenerationContext cgenc) throws ParseError {
    cgenc.setName(universe.symbolFor(text));
    expect(Identifier);
    expect(Equal);

    superclass(cgenc);

    expect(NewTerm);
    instanceFields(cgenc);

    while (isIdentifier(sym) || sym == Keyword || sym == OperatorSequence
        || symIn(binaryOpSyms)) {
      MethodGenerationContext mgenc = new MethodGenerationContext(cgenc);

      ExpressionTuple methodBody = method(mgenc);

      Invokable enforced   = mgenc.assemble(methodBody.en, true);
      Invokable unenforced = mgenc.assemble(methodBody.un, false);

      cgenc.addInstanceMethod(mgenc.assembleSInvokable(universe, enforced, unenforced));
    }

    if (accept(Separator)) {
      cgenc.setClassSide(true);
      classFields(cgenc);
      while (isIdentifier(sym) || sym == Keyword || sym == OperatorSequence
          || symIn(binaryOpSyms)) {
        MethodGenerationContext mgenc = new MethodGenerationContext(cgenc);

        ExpressionTuple methodBody = method(mgenc);

        Invokable enforced   = mgenc.assemble(methodBody.en, true);
        Invokable unenforced = mgenc.assemble(methodBody.un, false);

        cgenc.addClassMethod(mgenc.assembleSInvokable(universe, enforced, unenforced));
      }
    }
    expect(EndTerm);
  }

  private void superclass(final ClassGenerationContext cgenc) throws ParseError {
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
      if (superClass == null) {
        throw new ParseError("Super class " + superName.getString() +
            " could not be loaded", NONE, this);
      }

      cgenc.setInstanceFieldsOfSuper(superClass.getInstanceFields());
      cgenc.setClassFieldsOfSuper(superClass.getSOMClass().getInstanceFields());
    }
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

  private boolean expect(final Symbol s) throws ParseError {
    if (accept(s)) { return true; }

    throw new ParseError("Unexpected symbol. Expected %(expected)s, but found "
        + "%(found)s", s, this);
  }

  private boolean expectOneOf(final List<Symbol> ss) throws ParseError {
    if (acceptOneOf(ss)) { return true; }

    throw new ParseErrorWithSymbolList("Unexpected symbol. Expected one of " +
        "%(expected)s, but found %(found)s", ss, this);
  }

  private void instanceFields(final ClassGenerationContext cgenc) throws ParseError {
    if (accept(Or)) {
      while (isIdentifier(sym)) {
        String var = variable();
        cgenc.addInstanceField(universe.symbolFor(var));
      }
      expect(Or);
    }
  }

  private void classFields(final ClassGenerationContext cgenc) throws ParseError {
    if (accept(Or)) {
      while (isIdentifier(sym)) {
        String var = variable();
        cgenc.addClassField(universe.symbolFor(var));
      }
      expect(Or);
    }
  }

  private SourceSection getSource(final SourceCoordinate coord) {
    return source.createSection("method", coord.startLine,
        coord.startColumn, coord.charIndex,
        lexer.getNumberOfCharactersRead() - coord.charIndex);
  }

  private ExpressionTuple method(final MethodGenerationContext mgenc) throws ParseError {
    pattern(mgenc);
    expect(Equal);

    unenforcedAnnotation(mgenc);

    if (sym == Primitive) {
      mgenc.setPrimitive(true);
      primitiveBlock();
      return new ExpressionTuple(null, null);
    } else {
      return methodBlock(mgenc);
    }
  }

  private void unenforcedAnnotation(final MethodGenerationContext mgenc) throws ParseError {
    if (sym == Identifier) {
      if ("unenforced".equals(text)) {
        accept(Identifier);
        mgenc.setUnenforced();
      } else {
        throw new ParseError("Unexpected identifier: " + text, Identifier, this);
      }
    }
  }

  private void primitiveBlock() throws ParseError {
    expect(Primitive);
  }

  private void pattern(final MethodGenerationContext mgenc) throws ParseError {
    mgenc.addArgumentIfAbsent("self"); // TODO: can we do that optionally?
    switch (sym) {
      case Identifier:
      case Primitive:
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

  private void unaryPattern(final MethodGenerationContext mgenc) throws ParseError {
    SSymbol selector = unarySelector();
    mgenc.setSignature(selector);
  }

  private void binaryPattern(final MethodGenerationContext mgenc) throws ParseError {
    SSymbol selector = binarySelector();
    mgenc.setSignature(selector);

    String arg = argument();
    mgenc.addArgumentIfAbsent(arg);
  }

  private void keywordPattern(final MethodGenerationContext mgenc) throws ParseError {
    StringBuffer kw = new StringBuffer();
    do {
      kw.append(keyword());
      String arg = argument();
      mgenc.addArgumentIfAbsent(arg);
    }
    while (sym == Keyword);

    SSymbol selector = universe.symbolFor(kw.toString());
    mgenc.setSignature(selector);
  }

  private ExpressionTuple methodBlock(final MethodGenerationContext mgenc) throws ParseError {
    expect(NewTerm);
    ExpressionTuple methodBody = blockContents(mgenc);
    expect(EndTerm);

    return methodBody;
  }

  private SSymbol unarySelector() throws ParseError {
    return universe.symbolFor(identifier());
  }

  private SSymbol binarySelector() throws ParseError {
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

  private String identifier() throws ParseError {
    String s = new String(text);
    boolean isPrimitive = accept(Primitive);
    if (!isPrimitive) {
      expect(Identifier);
    }
    return s;
  }

  private String keyword() throws ParseError {
    String s = new String(text);
    expect(Keyword);

    return s;
  }

  private String argument() throws ParseError {
    return variable();
  }

  private ExpressionTuple blockContents(final MethodGenerationContext mgenc) throws ParseError {
    if (accept(Or)) {
      locals(mgenc);
      expect(Or);
    }
    return blockBody(mgenc);
  }

  private void locals(final MethodGenerationContext mgenc) throws ParseError {
    while (isIdentifier(sym)) {
      String var = variable();
      mgenc.addLocalIfAbsent(var);
    }
  }

  private ExpressionTuple blockBody(final MethodGenerationContext mgenc) throws ParseError {
    SourceCoordinate coord = getCoordinate();
    List<ExpressionNode> enforced   = new ArrayList<ExpressionNode>();
    List<ExpressionNode> unenforced = new ArrayList<ExpressionNode>();

    while (true) {
      if (accept(Exit)) {
        ExpressionTuple r = result(mgenc);
        enforced.add(r.en);
        unenforced.add(r.un);
        return createSequenceNode(coord, enforced, unenforced);
      } else if (sym == EndBlock) {
        return createSequenceNode(coord, enforced, unenforced);
      } else if (sym == EndTerm) {
        // the end of the method has been found (EndTerm) - make it implicitly
        // return "self"
        ExpressionTuple self = variableRead(mgenc, "self", getSource(getCoordinate()));
        enforced.add(self.en);
        unenforced.add(self.un);
        return createSequenceNode(coord, enforced, unenforced);
      }

      ExpressionTuple exp = expression(mgenc);
      enforced.add(exp.en);
      unenforced.add(exp.un);
      accept(Period);
    }
  }

  private ExpressionTuple createSequenceNode(final SourceCoordinate coord,
      final List<ExpressionNode> enforced, final List<ExpressionNode> unenforced) {
    SourceSection source = getSource(coord);
    assert enforced.size() == unenforced.size();
    if (enforced.size() == 0) {
      return tuple(createGlobalRead("nil", universe, source, true),
          createGlobalRead("nil", universe, source, false));
    } else if (enforced.size() == 1) {
      return tuple(enforced.get(0), unenforced.get(0));
    }

    return tuple(createSequence(enforced, source, true),
        createSequence(unenforced, source, false));
  }

  private ExpressionTuple result(final MethodGenerationContext mgenc) throws ParseError {
    ExpressionTuple exp = expression(mgenc);

    SourceCoordinate coord = getCoordinate();
    accept(Period);

    if (mgenc.isBlockMethod()) {
      SourceSection source = getSource(coord);
      return tuple(mgenc.getNonLocalReturn(exp.en, source, true),
          mgenc.getNonLocalReturn(exp.un, source, false));
    } else {
      return exp;
    }
  }

  private ExpressionTuple expression(final MethodGenerationContext mgenc) throws ParseError {
    peekForNextSymbolFromLexer();

    if (nextSym == Assign) {
      return assignation(mgenc);
    } else {
      return evaluation(mgenc);
    }
  }

  private ExpressionTuple assignation(final MethodGenerationContext mgenc) throws ParseError {
    return assignments(mgenc);
  }

  private ExpressionTuple assignments(final MethodGenerationContext mgenc) throws ParseError {
    SourceCoordinate coord = getCoordinate();

    if (!isIdentifier(sym)) {
      throw new ParseError("Assignments should always target variables or" +
                           " fields, but found instead a %(found)s",
                           Identifier, this);
    }
    String variable = assignment();

    peekForNextSymbolFromLexer();

    ExpressionTuple value;
    if (nextSym == Assign) {
      value = assignments(mgenc);
    } else {
      value = evaluation(mgenc);
    }

    return variableWrite(mgenc, variable, value, getSource(coord));
  }

  private String assignment() throws ParseError {
    String v = variable();
    expect(Assign);
    return v;
  }

  private ExpressionTuple evaluation(final MethodGenerationContext mgenc) throws ParseError {
    ExpressionTuple exp = primary(mgenc);
    if (isIdentifier(sym) || sym == Keyword || sym == OperatorSequence
        || symIn(binaryOpSyms)) {
      exp = messages(mgenc, exp);
    }
    return exp;
  }

  private ExpressionTuple primary(final MethodGenerationContext mgenc) throws ParseError {
    switch (sym) {
      case Identifier:
      case Primitive: {
        SourceCoordinate coord = getCoordinate();
        String v = variable();
        return variableRead(mgenc, v, getSource(coord));
      }
      case NewTerm: {
        return nestedTerm(mgenc);
      }
      case NewBlock: {
        SourceCoordinate coord = getCoordinate();
        MethodGenerationContext bgenc = new MethodGenerationContext(mgenc.getHolder(), mgenc);

        ExpressionTuple blockBody = nestedBlock(bgenc);

        Invokable enforced   = bgenc.assemble(blockBody.en, true);
        Invokable unenforced = bgenc.assemble(blockBody.un, false);

        SMethod blockMethod = (SMethod) bgenc.assembleSInvokable(universe, enforced, unenforced);
        mgenc.addEmbeddedBlockMethod(blockMethod);

        SourceSection source = getSource(coord);

        boolean withContext = bgenc.requiresContext();

        return tuple(createBlockNode(blockMethod, withContext, source, true),
            createBlockNode(blockMethod, withContext, source, false));
      }
      default: {
        return literal();
      }
    }
  }

  private String variable() throws ParseError {
    return identifier();
  }

  private ExpressionTuple messages(final MethodGenerationContext mgenc,
      final ExpressionTuple receiver) throws ParseError {
    ExpressionTuple msg;
    if (isIdentifier(sym)) {
      msg = unaryMessage(receiver);

      while (isIdentifier(sym)) {
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

  private ExpressionTuple unaryMessage(final ExpressionTuple receiver) throws ParseError {
    SourceCoordinate coord = getCoordinate();
    SSymbol selector = unarySelector();

    SourceSection source = getSource(coord);
    return tuple(createMessageSend(selector, new ExpressionNode[] {receiver.en}, source, true),
        createMessageSend(selector, new ExpressionNode[] {receiver.un}, source, false));
  }

  private ExpressionTuple binaryMessage(final MethodGenerationContext mgenc,
      final ExpressionTuple receiver) throws ParseError {
    SourceCoordinate coord = getCoordinate();
    SSymbol msg = binarySelector();
    ExpressionTuple operand = binaryOperand(mgenc);

    SourceSection source = getSource(coord);
    return tuple(createMessageSend(msg, new ExpressionNode[] {receiver.en, operand.en}, source, true),
        createMessageSend(msg, new ExpressionNode[] {receiver.un, operand.un}, source, false));
  }

  private ExpressionTuple binaryOperand(final MethodGenerationContext mgenc) throws ParseError {
    ExpressionTuple operand = primary(mgenc);

    // a binary operand can receive unaryMessages
    // Example: 2 * 3 asString
    //   is evaluated as 2 * (3 asString)
    while (isIdentifier(sym)) {
      operand = unaryMessage(operand);
    }
    return operand;
  }

  private ExpressionTuple keywordMessage(final MethodGenerationContext mgenc,
      final ExpressionTuple receiver) throws ParseError {
    SourceCoordinate coord = getCoordinate();
    List<ExpressionNode> enforcedArgs   = new ArrayList<ExpressionNode>();
    List<ExpressionNode> unenforcedArgs = new ArrayList<ExpressionNode>();
    StringBuffer kw = new StringBuffer();

    enforcedArgs.add(receiver.en);
    unenforcedArgs.add(receiver.un);

    do {
      kw.append(keyword());
      ExpressionTuple f = formula(mgenc);
      enforcedArgs.add(f.en);
      unenforcedArgs.add(f.un);
    }
    while (sym == Keyword);

    SSymbol msg = universe.symbolFor(kw.toString());

    SourceSection source = getSource(coord);

    return tuple(createMessageSend(msg, enforcedArgs, source, true),
        createMessageSend(msg, unenforcedArgs, source, false));
  }

  private ExpressionTuple formula(final MethodGenerationContext mgenc) throws ParseError {
    ExpressionTuple operand = binaryOperand(mgenc);

    while (sym == OperatorSequence || symIn(binaryOpSyms)) {
      operand = binaryMessage(mgenc, operand);
    }
    return operand;
  }

  private ExpressionTuple nestedTerm(final MethodGenerationContext mgenc) throws ParseError {
    expect(NewTerm);
    ExpressionTuple exp = expression(mgenc);
    expect(EndTerm);
    return exp;
  }

  private ExpressionTuple literal() throws ParseError {
    switch (sym) {
      case Pound:     return literalSymbol();
      case STString:  return literalString();
      default:        return literalNumber();
    }
  }

  private ExpressionTuple literalNumber() throws ParseError {
    SourceCoordinate coord = getCoordinate();

    if (sym == Minus) {
      return negativeDecimal(coord);
    } else {
      return literalDecimal(false, coord);
    }
  }

  private ExpressionTuple literalDecimal(final boolean isNegative, final SourceCoordinate coord) throws ParseError {
    if (sym == Integer) {
      return literalInteger(isNegative, coord);
    } else {
      assert sym == Double;
      return literalDouble(isNegative, coord);
    }
  }

  private ExpressionTuple negativeDecimal(final SourceCoordinate coord) throws ParseError {
    expect(Minus);
    return literalDecimal(true, coord);
  }

  private ExpressionTuple literalInteger(final boolean isNegative,
      final SourceCoordinate coord) throws ParseError {
    try {
       long i = Long.parseLong(text);
       if (isNegative) {
         i = 0 - i;
       }
       expect(Integer);

       SourceSection source = getSource(coord);
       if (i < Long.MIN_VALUE || i > Long.MAX_VALUE) {
         BigInteger bi = BigInteger.valueOf(i);
         return tuple(new BigIntegerLiteralNode(bi, source, true),
             new BigIntegerLiteralNode(bi, source, false));
       } else {
         return tuple(new IntegerLiteralNode(i, source, true),
             new IntegerLiteralNode(i, source, false));
       }
    } catch (NumberFormatException e) {
      throw new ParseError("Could not parse integer. Expected a number but " +
                           "got '" + text + "'", NONE, this);
    }
  }

  private ExpressionTuple literalDouble(final boolean isNegative, final SourceCoordinate coord) throws ParseError {
    try {
      double d = java.lang.Double.parseDouble(text);
      if (isNegative) {
        d = 0.0 - d;
      }
      expect(Double);
      SourceSection source = getSource(coord);
      return tuple(new DoubleLiteralNode(d, source, true),
          new DoubleLiteralNode(d, source, false));
    } catch (NumberFormatException e) {
      throw new ParseError("Could not parse double. Expected a number but " +
          "got '" + text + "'", NONE, this);
    }
  }

  private ExpressionTuple literalSymbol() throws ParseError {
    SourceCoordinate coord = getCoordinate();

    SSymbol symb;
    expect(Pound);
    if (sym == STString) {
      String s = string();
      symb = universe.symbolFor(s);
    } else {
      symb = selector();
    }
    SourceSection source = getSource(coord);
    return tuple(new SymbolLiteralNode(symb, source, true),
        new SymbolLiteralNode(symb, source, false));
  }

  private ExpressionTuple literalString() throws ParseError {
    SourceCoordinate coord = getCoordinate();
    String s = string();

    SourceSection source = getSource(coord);
    return tuple(new StringLiteralNode(s, source, true),
        new StringLiteralNode(s, source, false));
  }

  private SSymbol selector() throws ParseError {
    if (sym == OperatorSequence || symIn(singleOpSyms)) {
      return binarySelector();
    } else if (sym == Keyword || sym == KeywordSequence) {
      return keywordSelector();
    } else {
      return unarySelector();
    }
  }

  private SSymbol keywordSelector() throws ParseError {
    String s = new String(text);
    expectOneOf(keywordSelectorSyms);
    SSymbol symb = universe.symbolFor(s);
    return symb;
  }

  private String string() throws ParseError {
    String s = new String(text);
    expect(STString);
    return s;
  }

  private ExpressionTuple nestedBlock(final MethodGenerationContext mgenc) throws ParseError {
    expect(NewBlock);

    mgenc.addArgumentIfAbsent("$blockSelf");

    if (sym == Colon) {
      blockPattern(mgenc);
    }

    // generate Block signature
    String blockSig = "$blockMethod@" + lexer.getCurrentLineNumber() + "@" + lexer.getCurrentColumn();
    int argSize = mgenc.getNumberOfArguments();
    for (int i = 1; i < argSize; i++) {
      blockSig += ":";
    }

    mgenc.setSignature(universe.symbolFor(blockSig));

    ExpressionTuple expressions = blockContents(mgenc);

    expect(EndBlock);

    return expressions;
  }

  private void blockPattern(final MethodGenerationContext mgenc) throws ParseError {
    blockArguments(mgenc);
    expect(Or);
  }

  private void blockArguments(final MethodGenerationContext mgenc) throws ParseError {
    do {
      expect(Colon);
      String arg = argument();
      mgenc.addArgumentIfAbsent(arg);
    }
    while (sym == Colon);
  }

  private ExpressionTuple variableRead(final MethodGenerationContext mgenc,
      final String variableName, final SourceSection source) {
    // we need to handle super special here
    if ("super".equals(variableName)) {
      return tuple(mgenc.getSuperReadNode(source, true),
          mgenc.getSuperReadNode(source, false));
    }

    // now look up first local variables, or method arguments
    Variable variable = mgenc.getVariable(variableName);
    if (variable != null) {
      return tuple(mgenc.getLocalReadNode(variableName, source, true),
          mgenc.getLocalReadNode(variableName, source, false));
    }

    // then object fields
    SSymbol varName = universe.symbolFor(variableName);
    AbstractFieldReadNode enforcedFieldRead = mgenc.getObjectFieldRead(varName, source, true);

    if (enforcedFieldRead != null) {
      return tuple(enforcedFieldRead,
          mgenc.getObjectFieldRead(varName, source, false));
    }

    // and finally assume it is a global
    return tuple(mgenc.getGlobalRead(varName, source, true),
        mgenc.getGlobalRead(varName, source, false));
  }

  private ExpressionTuple variableWrite(final MethodGenerationContext mgenc,
      final String variableName, final ExpressionTuple exp,
      final SourceSection source) {
    Local variable = mgenc.getLocal(variableName);

    if (variable != null) {
      return tuple(mgenc.getLocalWriteNode(variableName, exp.en, source, true),
          mgenc.getLocalWriteNode(variableName, exp.un, source, false));
    }

    SSymbol fieldName = universe.symbolFor(variableName);
    AbstractFieldWriteNode enforcedFieldWrite = mgenc.getObjectFieldWrite(fieldName, exp.en,
        universe, source, true);

    if (enforcedFieldWrite != null) {
      return tuple(enforcedFieldWrite,
          mgenc.getObjectFieldWrite(fieldName, exp.un, universe, source, false));
    } else {
      throw new RuntimeException("Neither a variable nor a field found "
          + "in current scope that is named " + variableName + ". Arguments are read-only.");
    }
  }

  private void getSymbolFromLexer() {
    sym  = lexer.getSym();
    text = lexer.getText();
  }

  private void peekForNextSymbolFromLexer() {
    nextSym = lexer.peek();
  }

  private static boolean isIdentifier(final Symbol sym) {
    return sym == Identifier || sym == Primitive;
  }

  private static boolean printableSymbol(final Symbol sym) {
    return sym == Integer || sym == Double || sym.compareTo(STString) >= 0;
  }
}
