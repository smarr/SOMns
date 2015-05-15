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
import static som.compiler.Symbol.BeginComment;
import static som.compiler.Symbol.Colon;
import static som.compiler.Symbol.Comma;
import static som.compiler.Symbol.Div;
import static som.compiler.Symbol.Double;
import static som.compiler.Symbol.EndBlock;
import static som.compiler.Symbol.EndComment;
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
import static som.compiler.Symbol.STString;
import static som.compiler.Symbol.Star;
import static som.interpreter.SNodeFactory.createGlobalRead;
import static som.interpreter.SNodeFactory.createMessageSend;
import static som.interpreter.SNodeFactory.createSequence;
import static som.vm.Symbols.symbolFor;

import java.io.Reader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import som.compiler.Lexer.SourceCoordinate;
import som.compiler.Variable.Local;
import som.interpreter.SNodeFactory;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.FieldNode.FieldReadNode;
import som.interpreter.nodes.FieldNode.FieldWriteNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.interpreter.nodes.literals.BigIntegerLiteralNode;
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.literals.BlockNode.BlockNodeWithContext;
import som.interpreter.nodes.literals.DoubleLiteralNode;
import som.interpreter.nodes.literals.IntegerLiteralNode;
import som.interpreter.nodes.literals.LiteralNode;
import som.interpreter.nodes.literals.StringLiteralNode;
import som.interpreter.nodes.literals.SymbolLiteralNode;
import som.interpreter.nodes.specialized.BooleanInlinedLiteralNode.AndInlinedLiteralNode;
import som.interpreter.nodes.specialized.BooleanInlinedLiteralNode.OrInlinedLiteralNode;
import som.interpreter.nodes.specialized.IfInlinedLiteralNode;
import som.interpreter.nodes.specialized.IfTrueIfFalseInlinedLiteralsNode;
import som.interpreter.nodes.specialized.IntToDoInlinedLiteralsNodeGen;
import som.interpreter.nodes.specialized.whileloops.WhileInlinedLiteralsNode;
import som.vm.NotYetImplementedException;
import som.vm.Universe;
import som.vmobjects.SInvokable.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class Parser {

  private final Universe            universe;
  private final Lexer               lexer;
  private final Source              source;

  private Symbol                    sym;
  private String                    text;
  private Symbol                    nextSym;

  private SourceSection             lastMethodsSourceSection;

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

  @Override
  public String toString() {
    return "Parser(" + source.getName() + ", " + this.getCoordinate().toString() + ")";
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

  public Parser(final Reader reader, final long fileSize, final Source source, final Universe universe) {
    this.universe = universe;
    this.source   = source;

    sym = NONE;
    lexer = new Lexer(reader, fileSize);
    nextSym = NONE;
    getSymbolFromLexer();
  }

  private SourceCoordinate getCoordinate() {
    return lexer.getStartCoordinate();
  }

  public void classDeclaration(final ClassBuilder clsBuilder) throws ParseError {
    classDeclaration(AccessModifier.PUBLIC, clsBuilder);
  }

  private void classDeclaration(final AccessModifier accessModifier,
      final ClassBuilder clsBuilder) throws ParseError {
    expectIdentifier("class");
    String className = text;
    expect(Identifier);

    clsBuilder.setName(symbolFor(className));

    MethodBuilder primaryFactory = clsBuilder.getInitializerMethodBuilder();

    // Newspeak-spec: this is not strictly sufficient for Newspeak
    //                it could also parse a binary selector here, I think
    //                but, doesn't seem so useful, so, let's keep it simple
    if (isIdentifier(sym) || sym == Keyword) {
      messagePattern(primaryFactory);
    }

    expect(Equal);

    inheritanceListAndOrBody(clsBuilder);
  }

  private void inheritanceListAndOrBody(final ClassBuilder clsBuilder) throws ParseError {
    if (sym == NewTerm) {
      defaultSuperclassAndBody(clsBuilder);
    } else {
      explicitInheritanceListAndOrBody(clsBuilder);
    }
  }

  private void defaultSuperclassAndBody(final ClassBuilder clsBuilder) throws ParseError {
    MethodBuilder def = clsBuilder.getInstantiationMethodBuilder();
    ExpressionNode selfRead = def.getReadNode("self", null);
    AbstractMessageSendNode superClass = SNodeFactory.createMessageSend(
        symbolFor("Object"), new ExpressionNode[] {selfRead}, null);

    clsBuilder.setSuperClassResolution(superClass);
    classBody(clsBuilder);
  }

  private void explicitInheritanceListAndOrBody(final ClassBuilder clsBuilder) {
    throw new NotYetImplementedException();
  }

  private void classBody(final ClassBuilder clsBuilder) throws ParseError {
    classHeader(clsBuilder);
    sideDeclaration(clsBuilder);
    if (sym == Colon) {
      classSideDecl(clsBuilder);
    }
  }

  private void classSideDecl(final ClassBuilder clsBuilder) throws ParseError {
    expect(Colon);
    expect(NewTerm);
    category(clsBuilder);
    expect(EndTerm);
  }

  private void classHeader(final ClassBuilder clsBuilder) throws ParseError {
    expect(NewTerm);
    if (sym == BeginComment) {
      classComment(clsBuilder);
    }
    if (sym == Or) {
      slotDeclarations(clsBuilder);
    }

    if (sym != EndTerm) {
      initExprs(clsBuilder);
    }
    expect(EndTerm);
  }

  private void classComment(final ClassBuilder clsBuilder) throws ParseError {
    // TODO: capture comment and add it to class
    comment();
  }

  private void comment() throws ParseError {
    expect(BeginComment);

    while (sym != EndComment) {
      if (sym == BeginComment) {
        comment();
      } else {
        getSymbolFromLexer();
      }
    }
    expect(EndComment);
  }

  private void slotDeclarations(final ClassBuilder clsBuilder) throws ParseError {
    // Newspeak-speak: we do not support simSlotDecls, i.e.,
    //                 simultaneous slots clauses (spec 6.3.2)
    expect(Or);

    while (sym != Or) {
      slotDefinition(clsBuilder);
    }
    expect(Or);
  }

  private void slotDefinition(final ClassBuilder clsBuilder) throws ParseError {
    AccessModifier acccessModifier = accessModifier();

    String slotName = slotDecl();
    boolean immutable;

    if (accept(Equal)) {
      immutable = true;
    } else {
      immutable = false;
      // TODO: need to parse tokenFromSymbol: #’::=’
      throw new NotYetImplementedException();
    }

    ExpressionNode init = expression(clsBuilder.getInitializerMethodBuilder());
    clsBuilder.addSlot(symbolFor(slotName), acccessModifier, immutable, init);

    expect(Period);
  }

  private AccessModifier accessModifier() {
    if (sym == Identifier) {
      if (acceptIdentifier("private"))   { return AccessModifier.PRIVATE;   }
      if (acceptIdentifier("protected")) { return AccessModifier.PROTECTED; }
      if (acceptIdentifier("public"))    { return AccessModifier.PUBLIC;    }
    }
    return AccessModifier.PUBLIC;
  }

  private String slotDecl() throws ParseError {
    return identifier();
  }

  private void initExprs(final ClassBuilder clsBuilder) throws ParseError {
    MethodBuilder initializer = clsBuilder.getInitializerMethodBuilder();
    clsBuilder.addInitializerExpression(expression(initializer));

    while (accept(Period)) {
      if (sym != EndTerm) {
        clsBuilder.addInitializerExpression(expression(initializer));
      }
    }
  }

  private void sideDeclaration(final ClassBuilder clsBuilder) throws ParseError {
    expect(NewTerm);
    while (canAcceptThisOrNextIdentifier("class")) {
      nestedClassDeclaration(clsBuilder);
    }

    while (sym != EndTerm) {
      category(clsBuilder);
    }

    expect(EndTerm);
  }

  private void nestedClassDeclaration(final ClassBuilder clsBuilder) throws ParseError {
    AccessModifier accessModifier = accessModifier();
    classDeclaration(accessModifier, clsBuilder);
  }

  private void category(final ClassBuilder clsBuilder) throws ParseError {
    String categoryName;
    // Newspeak-spec: this is not conform with Newspeak,
    //                as the category is normally not optional
    if (sym == STString) {
      categoryName = string();
    } else {
      categoryName = "";
    }
    while (sym != EndTerm) {
      methodDeclaration(clsBuilder, symbolFor(categoryName));
    }
  }
//
//    expect(NewTerm);
//
//    while (isIdentifier(sym) || sym == Keyword || sym == OperatorSequence
//        || symIn(binaryOpSyms)) {
//      MethodGenerationContext builder = new MethodGenerationContext(clsBuilder);
//
//      ExpressionNode methodBody = method(builder);
//
//      clsBuilder.addInstanceMethod(builder.assemble(methodBody, lastMethodsSourceSection));
//    }
//
//    //TODO: cleanup
//    //if (accept(Separator)) {
//      clsBuilder.setClassSide(true);
//      classFields(clsBuilder);
//      while (isIdentifier(sym) || sym == Keyword || sym == OperatorSequence
//          || symIn(binaryOpSyms)) {
//        MethodGenerationContext builder = new MethodGenerationContext(clsBuilder);
//
//        ExpressionNode methodBody = method(builder);
//        clsBuilder.addClassMethod(builder.assemble(methodBody, lastMethodsSourceSection));
//      }
//    //}
//    expect(EndTerm);
//  }

  private boolean symIn(final List<Symbol> ss) {
    return ss.contains(sym);
  }

  private boolean canAcceptThisOrNextIdentifier(final String identifier) {
    if (sym == Identifier && identifier.equals(text)) { return true; }
    peekForNextSymbolFromLexer();
    return nextSym == Identifier && identifier.equals(nextSym);
  }

  private boolean acceptIdentifier(final String identifier) {
    if (sym == Identifier && identifier.equals(text)) {
      accept(Identifier);
      return true;
    }
    return false;
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

  private boolean expectIdentifier(final String identifier) throws ParseError {
    if (acceptIdentifier(identifier)) { return true; }

    throw new ParseError("Unexpected token. Expected '" + identifier +
        "', but found %(found)s", Identifier, this);
  }

  private void expect(final Symbol s) throws ParseError {
    if (accept(s)) { return; }

    throw new ParseError("Unexpected symbol. Expected %(expected)s, but found "
        + "%(found)s", s, this);
  }

  private boolean expectOneOf(final List<Symbol> ss) throws ParseError {
    if (acceptOneOf(ss)) { return true; }

    throw new ParseErrorWithSymbolList("Unexpected symbol. Expected one of " +
        "%(expected)s, but found %(found)s", ss, this);
  }

  private SourceSection getSource(final SourceCoordinate coord) {
    assert lexer.getNumberOfCharactersRead() - coord.charIndex >= 0;
    return source.createSection("method", coord.startLine,
        coord.startColumn, coord.charIndex,
        lexer.getNumberOfCharactersRead() - coord.charIndex);
  }

  private void methodDeclaration(final ClassBuilder clsBuilder,
      final SSymbol category) throws ParseError {
    SourceCoordinate coord = getCoordinate();

    AccessModifier accessModifier = accessModifier();
    MethodBuilder builder = new MethodBuilder(clsBuilder);

    messagePattern(builder);
    expect(Equal);
    ExpressionNode body = methodBlock(builder);
    SMethod meth = (SMethod) builder.assemble(body, accessModifier, category, getSource(coord));
    clsBuilder.addMethod(meth);
  }

  private void messagePattern(final MethodBuilder builder) throws ParseError {
    builder.addArgumentIfAbsent("self");
    switch (sym) {
      case Identifier:
        unaryPattern(builder);
        break;
      case Keyword:
        keywordPattern(builder);
        break;
      default:
        binaryPattern(builder);
        break;
    }
  }

  private void unaryPattern(final MethodBuilder builder) throws ParseError {
    builder.setSignature(unarySelector());
  }

  private void binaryPattern(final MethodBuilder builder) throws ParseError {
    builder.setSignature(binarySelector());
    builder.addArgumentIfAbsent(argument());
  }

  private void keywordPattern(final MethodBuilder builder) throws ParseError {
    StringBuffer kw = new StringBuffer();
    do {
      kw.append(keyword());
      builder.addArgumentIfAbsent(argument());
    }
    while (sym == Keyword);

    builder.setSignature(symbolFor(kw.toString()));
  }

  private ExpressionNode methodBlock(final MethodBuilder builder) throws ParseError {
    expect(NewTerm);
    SourceCoordinate coord = getCoordinate();
    ExpressionNode methodBody = blockContents(builder);
    lastMethodsSourceSection = getSource(coord);
    expect(EndTerm);

    return methodBody;
  }

  private SSymbol unarySelector() throws ParseError {
    return symbolFor(identifier());
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

    return symbolFor(s);
  }

  private String identifier() throws ParseError {
    String s = new String(text);
    expect(Identifier);
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

  private ExpressionNode blockContents(final MethodBuilder builder) throws ParseError {
    if (accept(Or)) {
      locals(builder);
      expect(Or);
    }
    return blockBody(builder);
  }

  private void locals(final MethodBuilder builder) throws ParseError {
    while (isIdentifier(sym)) {
      builder.addLocalIfAbsent(variable());
    }
  }

  private ExpressionNode blockBody(final MethodBuilder builder) throws ParseError {
    SourceCoordinate coord = getCoordinate();
    List<ExpressionNode> expressions = new ArrayList<ExpressionNode>();

    while (true) {
      if (accept(Exit)) {
        expressions.add(result(builder));
        return createSequenceNode(coord, expressions);
      } else if (sym == EndBlock) {
        return createSequenceNode(coord, expressions);
      } else if (sym == EndTerm) {
        // the end of the method has been found (EndTerm) - make it implicitly
        // return "self"
        ExpressionNode self = variableRead(builder, "self", getSource(getCoordinate()));
        expressions.add(self);
        return createSequenceNode(coord, expressions);
      }

      expressions.add(expression(builder));
      accept(Period);
    }
  }

  private ExpressionNode createSequenceNode(final SourceCoordinate coord,
      final List<ExpressionNode> expressions) {
    if (expressions.size() == 0) {
      return createGlobalRead("nil", universe, getSource(coord));
    } else if (expressions.size() == 1) {
      return expressions.get(0);
    }
    return createSequence(expressions, getSource(coord));
  }

  private ExpressionNode result(final MethodBuilder builder) throws ParseError {
    SourceCoordinate coord = getCoordinate();

    ExpressionNode exp = expression(builder);
    accept(Period);

    if (builder.isBlockMethod()) {
      return builder.getNonLocalReturn(exp, getSource(coord));
    } else {
      return exp;
    }
  }

  private ExpressionNode expression(final MethodBuilder builder) throws ParseError {
    peekForNextSymbolFromLexer();

    if (nextSym == Assign) {
      return assignation(builder);
    } else {
      return evaluation(builder);
    }
  }

  private ExpressionNode assignation(final MethodBuilder builder) throws ParseError {
    return assignments(builder);
  }

  private ExpressionNode assignments(final MethodBuilder builder) throws ParseError {
    SourceCoordinate coord = getCoordinate();

    if (!isIdentifier(sym)) {
      throw new ParseError("Assignments should always target variables or" +
                           " fields, but found instead a %(found)s",
                           Symbol.Identifier, this);
    }
    String variable = assignment();

    peekForNextSymbolFromLexer();

    ExpressionNode value;
    if (nextSym == Assign) {
      value = assignments(builder);
    } else {
      value = evaluation(builder);
    }

    return variableWrite(builder, variable, value, getSource(coord));
  }

  private String assignment() throws ParseError {
    String v = variable();
    expect(Assign);
    return v;
  }

  private ExpressionNode evaluation(final MethodBuilder builder) throws ParseError {
    ExpressionNode exp = primary(builder);
    if (isIdentifier(sym) || sym == Keyword || sym == OperatorSequence
        || symIn(binaryOpSyms)) {
      exp = messages(builder, exp);
    }
    return exp;
  }

  private ExpressionNode primary(final MethodBuilder builder) throws ParseError {
    switch (sym) {
      case Identifier: {
        SourceCoordinate coord = getCoordinate();
        String v = variable();
        return variableRead(builder, v, getSource(coord));
      }
      case NewTerm: {
        return nestedTerm(builder);
      }
      case NewBlock: {
        SourceCoordinate coord = getCoordinate();
        MethodBuilder bgenc = new MethodBuilder(builder.getHolder(), builder);

        ExpressionNode blockBody = nestedBlock(bgenc);

        SMethod blockMethod = (SMethod) bgenc.assemble(blockBody,
            AccessModifier.NOT_APPLICABLE, null, lastMethodsSourceSection);
        builder.addEmbeddedBlockMethod(blockMethod);

        if (bgenc.requiresContext()) {
          return new BlockNodeWithContext(blockMethod, getSource(coord));
        } else {
          return new BlockNode(blockMethod, getSource(coord));
        }
      }
      default: {
        return literal();
      }
    }
  }

  private String variable() throws ParseError {
    return identifier();
  }

  private ExpressionNode messages(final MethodBuilder builder,
      final ExpressionNode receiver) throws ParseError {
    ExpressionNode msg;
    if (isIdentifier(sym)) {
      msg = unaryMessage(receiver);

      while (isIdentifier(sym)) {
        msg = unaryMessage(msg);
      }

      while (sym == OperatorSequence || symIn(binaryOpSyms)) {
        msg = binaryMessage(builder, msg);
      }

      if (sym == Keyword) {
        msg = keywordMessage(builder, msg);
      }
    } else if (sym == OperatorSequence || symIn(binaryOpSyms)) {
      msg = binaryMessage(builder, receiver);

      while (sym == OperatorSequence || symIn(binaryOpSyms)) {
        msg = binaryMessage(builder, msg);
      }

      if (sym == Keyword) {
        msg = keywordMessage(builder, msg);
      }
    } else {
      msg = keywordMessage(builder, receiver);
    }
    return msg;
  }

  private AbstractMessageSendNode unaryMessage(final ExpressionNode receiver) throws ParseError {
    SourceCoordinate coord = getCoordinate();
    SSymbol selector = unarySelector();
    return createMessageSend(selector, new ExpressionNode[] {receiver},
        getSource(coord));
  }

  private AbstractMessageSendNode binaryMessage(final MethodBuilder builder,
      final ExpressionNode receiver) throws ParseError {
    SourceCoordinate coord = getCoordinate();
    SSymbol msg = binarySelector();
    ExpressionNode operand = binaryOperand(builder);

    return createMessageSend(msg, new ExpressionNode[] {receiver, operand},
        getSource(coord));
  }

  private ExpressionNode binaryOperand(final MethodBuilder builder) throws ParseError {
    ExpressionNode operand = primary(builder);

    // a binary operand can receive unaryMessages
    // Example: 2 * 3 asString
    //   is evaluated as 2 * (3 asString)
    while (isIdentifier(sym)) {
      operand = unaryMessage(operand);
    }
    return operand;
  }

  private ExpressionNode keywordMessage(final MethodBuilder builder,
      final ExpressionNode receiver) throws ParseError {
    SourceCoordinate coord = getCoordinate();
    List<ExpressionNode> arguments = new ArrayList<ExpressionNode>();
    StringBuffer         kw        = new StringBuffer();

    arguments.add(receiver);

    do {
      kw.append(keyword());
      arguments.add(formula(builder));
    }
    while (sym == Keyword);

    String msgStr = kw.toString();
    SSymbol msg = symbolFor(msgStr);

    SourceSection source = getSource(coord);

    if (msg.getNumberOfSignatureArguments() == 2) {
      if (arguments.get(1) instanceof LiteralNode) {
        if ("ifTrue:".equals(msgStr)) {
          ExpressionNode inlinedBody = ((LiteralNode) arguments.get(1)).inline(builder);
          return new IfInlinedLiteralNode(arguments.get(0), true, inlinedBody,
              arguments.get(1), source);
        } else if ("ifFalse:".equals(msgStr)) {
          ExpressionNode inlinedBody = ((LiteralNode) arguments.get(1)).inline(builder);
          return new IfInlinedLiteralNode(arguments.get(0), false, inlinedBody,
              arguments.get(1), source);
        } else if ("whileTrue:".equals(msgStr)) {
          ExpressionNode inlinedCondition = ((LiteralNode) arguments.get(0)).inline(builder);
          ExpressionNode inlinedBody      = ((LiteralNode) arguments.get(1)).inline(builder);
          return new WhileInlinedLiteralsNode(inlinedCondition, inlinedBody,
              true, arguments.get(0), arguments.get(1), source);
        } else if ("whileFalse:".equals(msgStr)) {
          ExpressionNode inlinedCondition = ((LiteralNode) arguments.get(0)).inline(builder);
          ExpressionNode inlinedBody      = ((LiteralNode) arguments.get(1)).inline(builder);
          return new WhileInlinedLiteralsNode(inlinedCondition, inlinedBody,
              false, arguments.get(0), arguments.get(1), source);
        } else if ("or:".equals(msgStr) || "||".equals(msgStr)) {
          ExpressionNode inlinedArg = ((LiteralNode) arguments.get(1)).inline(builder);
          return new OrInlinedLiteralNode(arguments.get(0), inlinedArg, arguments.get(1), source);
        } else if ("and:".equals(msgStr) || "&&".equals(msgStr)) {
          ExpressionNode inlinedArg = ((LiteralNode) arguments.get(1)).inline(builder);
          return new AndInlinedLiteralNode(arguments.get(0), inlinedArg, arguments.get(1), source);
        }
      }
    } else if (msg.getNumberOfSignatureArguments() == 3) {
      if ("ifTrue:ifFalse:".equals(msgStr) &&
          arguments.get(1) instanceof LiteralNode && arguments.get(2) instanceof LiteralNode) {
        ExpressionNode inlinedTrueNode  = ((LiteralNode) arguments.get(1)).inline(builder);
        ExpressionNode inlinedFalseNode = ((LiteralNode) arguments.get(2)).inline(builder);
        return new IfTrueIfFalseInlinedLiteralsNode(arguments.get(0),
            inlinedTrueNode, inlinedFalseNode, arguments.get(1), arguments.get(2),
            source);
      } else if ("to:do:".equals(msgStr) &&
          arguments.get(2) instanceof LiteralNode) {
        Local loopIdx = builder.addLocal("i:" + source.getCharIndex());
        ExpressionNode inlinedBody = ((LiteralNode) arguments.get(2)).inline(builder, loopIdx);
        return IntToDoInlinedLiteralsNodeGen.create(inlinedBody, loopIdx.getSlot(),
            arguments.get(2), source, arguments.get(0), arguments.get(1));
      }
    }

    return createMessageSend(msg, arguments.toArray(new ExpressionNode[0]),
        source);
  }

  private ExpressionNode formula(final MethodBuilder builder) throws ParseError {
    ExpressionNode operand = binaryOperand(builder);

    while (sym == OperatorSequence || symIn(binaryOpSyms)) {
      operand = binaryMessage(builder, operand);
    }
    return operand;
  }

  private ExpressionNode nestedTerm(final MethodBuilder builder) throws ParseError {
    expect(NewTerm);
    ExpressionNode exp = expression(builder);
    expect(EndTerm);
    return exp;
  }

  private LiteralNode literal() throws ParseError {
    switch (sym) {
      case Pound:     return literalSymbol();
      case STString:  return literalString();
      default:        return literalNumber();
    }
  }

  private LiteralNode literalNumber() throws ParseError {
    SourceCoordinate coord = getCoordinate();

    if (sym == Minus) {
      return negativeDecimal(coord);
    } else {
      return literalDecimal(false, coord);
    }
  }

  private LiteralNode literalDecimal(final boolean isNegative, final SourceCoordinate coord) throws ParseError {
    if (sym == Integer) {
      return literalInteger(isNegative, coord);
    } else {
      assert sym == Double;
      return literalDouble(isNegative, coord);
    }
  }

  private LiteralNode negativeDecimal(final SourceCoordinate coord) throws ParseError {
    expect(Minus);
    return literalDecimal(true, coord);
  }

  private LiteralNode literalInteger(final boolean isNegative,
      final SourceCoordinate coord) throws ParseError {
    try {
       long i = Long.parseLong(text);
       if (isNegative) {
         i = 0 - i;
       }
       expect(Integer);

       SourceSection source = getSource(coord);
       if (i < Long.MIN_VALUE || i > Long.MAX_VALUE) {
         return new BigIntegerLiteralNode(BigInteger.valueOf(i), source);
       } else {
         return new IntegerLiteralNode(i, source);
       }
    } catch (NumberFormatException e) {
      throw new ParseError("Could not parse integer. Expected a number but " +
                           "got '" + text + "'", NONE, this);
    }
  }

  private LiteralNode literalDouble(final boolean isNegative, final SourceCoordinate coord) throws ParseError {
    try {
      double d = java.lang.Double.parseDouble(text);
      if (isNegative) {
        d = 0.0 - d;
      }
      expect(Double);
      SourceSection source = getSource(coord);
      return new DoubleLiteralNode(d, source);
    } catch (NumberFormatException e) {
      throw new ParseError("Could not parse double. Expected a number but " +
          "got '" + text + "'", NONE, this);
    }
  }

  private LiteralNode literalSymbol() throws ParseError {
    SourceCoordinate coord = getCoordinate();

    SSymbol symb;
    expect(Pound);
    if (sym == STString) {
      String s = string();
      symb = symbolFor(s);
    } else {
      symb = selector();
    }

    return new SymbolLiteralNode(symb, getSource(coord));
  }

  private LiteralNode literalString() throws ParseError {
    SourceCoordinate coord = getCoordinate();
    String s = string();

    return new StringLiteralNode(s, getSource(coord));
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
    SSymbol symb = symbolFor(s);
    return symb;
  }

  private String string() throws ParseError {
    String s = new String(text);
    expect(STString);
    return s;
  }

  private ExpressionNode nestedBlock(final MethodBuilder builder) throws ParseError {
    expect(NewBlock);
    SourceCoordinate coord = getCoordinate();

    builder.addArgumentIfAbsent("$blockSelf");

    if (sym == Colon) {
      blockPattern(builder);
    }

    // generate Block signature
    String blockSig = "$blockMethod@" + lexer.getCurrentLineNumber() + "@" + lexer.getCurrentColumn();
    int argSize = builder.getNumberOfArguments();
    for (int i = 1; i < argSize; i++) {
      blockSig += ":";
    }

    builder.setSignature(symbolFor(blockSig));

    ExpressionNode expressions = blockContents(builder);

    lastMethodsSourceSection = getSource(coord);

    expect(EndBlock);

    return expressions;
  }

  private void blockPattern(final MethodBuilder builder) throws ParseError {
    blockArguments(builder);
    expect(Or);
  }

  private void blockArguments(final MethodBuilder builder) throws ParseError {
    do {
      expect(Colon);
      builder.addArgumentIfAbsent(argument());
    }
    while (sym == Colon);
  }

  private ExpressionNode variableRead(final MethodBuilder builder,
                                      final String variableName,
                                      final SourceSection source) {
    // we need to handle super special here
    if ("super".equals(variableName)) {
      return builder.getSuperReadNode(source);
    }

    // now look up first local variables, or method arguments
    Variable variable = builder.getVariable(variableName);
    if (variable != null) {
      return builder.getReadNode(variableName, source);
    }

    // then object fields
    SSymbol varName = symbolFor(variableName);
    FieldReadNode fieldRead = builder.getObjectFieldRead(varName, source);

    if (fieldRead != null) {
      return fieldRead;
    }

    // and finally assume it is a global
    return builder.getGlobalRead(varName, universe, source);
  }

  private ExpressionNode variableWrite(final MethodBuilder builder,
      final String variableName, final ExpressionNode exp, final SourceSection source) {
    Local variable = builder.getLocal(variableName);
    if (variable != null) {
      return builder.getWriteNode(variableName, exp, source);
    }

    SSymbol fieldName = symbolFor(variableName);
    FieldWriteNode fieldWrite = builder.getObjectFieldWrite(fieldName, exp, universe, source);

    if (fieldWrite != null) {
      return fieldWrite;
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
    return sym == Identifier;
  }

  private static boolean printableSymbol(final Symbol sym) {
    return sym == Integer || sym == Double || sym.compareTo(STString) >= 0;
  }
}
