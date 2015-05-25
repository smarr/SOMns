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
import static som.compiler.Symbol.SlotMutableAssign;
import static som.compiler.Symbol.Star;
import static som.interpreter.SNodeFactory.createMessageSend;
import static som.interpreter.SNodeFactory.createSequence;
import static som.vm.Symbols.symbolFor;

import java.io.Reader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import som.compiler.ClassBuilder.ClassDefinitionError;
import som.compiler.Lexer.Peek;
import som.compiler.Lexer.SourceCoordinate;
import som.compiler.Variable.Local;
import som.interpreter.SNodeFactory;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractUninitializedMessageSendNode;
import som.interpreter.nodes.OuterObjectRead;
import som.interpreter.nodes.literals.BigIntegerLiteralNode;
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.literals.BlockNode.BlockNodeWithContext;
import som.interpreter.nodes.literals.BooleanLiteralNode.FalseLiteralNode;
import som.interpreter.nodes.literals.BooleanLiteralNode.TrueLiteralNode;
import som.interpreter.nodes.literals.DoubleLiteralNode;
import som.interpreter.nodes.literals.IntegerLiteralNode;
import som.interpreter.nodes.literals.LiteralNode;
import som.interpreter.nodes.literals.NilLiteralNode;
import som.interpreter.nodes.literals.StringLiteralNode;
import som.interpreter.nodes.literals.SymbolLiteralNode;
import som.interpreter.nodes.specialized.BooleanInlinedLiteralNode.AndInlinedLiteralNode;
import som.interpreter.nodes.specialized.BooleanInlinedLiteralNode.OrInlinedLiteralNode;
import som.interpreter.nodes.specialized.IfInlinedLiteralNode;
import som.interpreter.nodes.specialized.IfTrueIfFalseInlinedLiteralsNode;
import som.interpreter.nodes.specialized.IntToDoInlinedLiteralsNodeGen;
import som.interpreter.nodes.specialized.whileloops.WhileInlinedLiteralsNode;
import som.vmobjects.SInvokable.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class Parser {

  private final Lexer               lexer;
  private final Source              source;

  private Symbol                    sym;
  private String                    text;
  private Symbol                    nextSym;
  private String                    nextText;

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

  public Parser(final Reader reader, final long fileSize, final Source source) {
    this.source   = source;

    sym = NONE;
    lexer = new Lexer(reader, fileSize);
    nextSym = NONE;
    getSymbolFromLexer();
  }

  SourceCoordinate getCoordinate() {
    return lexer.getStartCoordinate();
  }

  public void moduleDeclaration(final ClassBuilder clsBuilder)
      throws ParseError, ClassDefinitionError {
    comment();
    classDeclaration(clsBuilder);
  }

  private void classDeclaration(final ClassBuilder clsBuilder)
      throws ParseError, ClassDefinitionError {
    expectIdentifier("class", "Found unexpected token %(found)s. " +
      "Tried parsing a class declaration and expected 'class' instead.");
    String className = text;
    expect(Identifier);

    clsBuilder.setName(symbolFor(className));

    MethodBuilder primaryFactory = clsBuilder.getPrimaryFactoryMethodBuilder();

    // Newspeak-spec: this is not strictly sufficient for Newspeak
    //                it could also parse a binary selector here, I think
    //                but, doesn't seem so useful, so, let's keep it simple
    if (isIdentifier(sym) || sym == Keyword) {
      messagePattern(primaryFactory);
    } else {
      // in the standard case, the primary factory method is #new
      primaryFactory.addArgumentIfAbsent("self");
      primaryFactory.setSignature(symbolFor("new"));
    }
    clsBuilder.setupInitializerBasedOnPrimaryFactory();

    expect(Equal, "Unexpected symbol %(found)s."
        + " Tried to parse the class declaration of " + className
        + " and expect '=' before the (optional) inheritance declaration.");

    inheritanceListAndOrBody(clsBuilder);
  }

  private void inheritanceListAndOrBody(final ClassBuilder clsBuilder)
      throws ParseError, ClassDefinitionError {
    if (sym == NewTerm) {
      defaultSuperclassAndBody(clsBuilder);
    } else {
      explicitInheritanceListAndOrBody(clsBuilder);
    }
  }

  private void defaultSuperclassAndBody(final ClassBuilder clsBuilder)
      throws ParseError, ClassDefinitionError {
    MethodBuilder def = clsBuilder.getClassInstantiationMethodBuilder();
    ExpressionNode selfRead = def.getSelfRead(null);
    AbstractMessageSendNode superClass = SNodeFactory.createMessageSend(
        symbolFor("Object"), new ExpressionNode[] {selfRead}, null);
    clsBuilder.setSuperClassResolution(superClass);

    clsBuilder.setSuperclassFactorySend(
        clsBuilder.createStandardSuperFactorySend(), true);

    classBody(clsBuilder);
  }

  private void explicitInheritanceListAndOrBody(final ClassBuilder clsBuilder)
      throws ParseError, ClassDefinitionError {
    inheritanceClause(clsBuilder);
    // TODO: Newspeak-spec: mixinAppSuffix
    classBody(clsBuilder);
  }

  private void inheritanceClause(final ClassBuilder clsBuilder)
      throws ParseError, ClassDefinitionError {
    ExpressionNode rcvr = inheritancePrefix(clsBuilder);
    AbstractMessageSendNode superClassResolution = unaryMessage(rcvr);
    clsBuilder.setSuperClassResolution(superClassResolution);

    if (sym != NewTerm) {
      // This factory method on the super class is actually called as
      // initializer of the super class after object creation.
      // The Newspeak spec isn't entirely straight forward on that, but it says
      // that it is a runtime error if it is not the primary factory method.
      // Which for me implies that this one is special. And indeed, it is
      // used to create the proper initialize method, on which we rely here.
      ExpressionNode superFactorySend = messages(
          clsBuilder.getInitializerMethodBuilder(),
          clsBuilder.getInitializerMethodBuilder().getSuperReadNode(null));

      SSymbol initializerName = ClassBuilder.getInitializerName(
          ((AbstractUninitializedMessageSendNode) superFactorySend).getSelector());

      clsBuilder.setSuperclassFactorySend(
          MessageSendNode.adaptSymbol(
              initializerName,
              (AbstractUninitializedMessageSendNode) superFactorySend), false);
    } else {
      clsBuilder.setSuperclassFactorySend(
          clsBuilder.createStandardSuperFactorySend(), true);
    }
  }

  private ExpressionNode inheritancePrefix(final ClassBuilder clsBuilder)
      throws ParseError {
    MethodBuilder meth = clsBuilder.getClassInstantiationMethodBuilder();
    SourceCoordinate coord = getCoordinate();

    if (acceptIdentifier("self")) {
      return meth.getSelfRead(getSource(coord));
    } else if (acceptIdentifier("super")) {
      return meth.getSuperReadNode(getSource(coord));
    } else if (acceptIdentifier("outer")) {
      ExpressionNode self = meth.getSelfRead(getSource(coord));
      return unaryMessage(self);
    } else {
      // TODO: this should probably be an implicit send!!
      return meth.getSelfRead(getSource(coord));
    }
  }

  private void classBody(final ClassBuilder clsBuilder)
      throws ParseError, ClassDefinitionError {
    classHeader(clsBuilder);
    sideDeclaration(clsBuilder);
    if (sym == Colon) {
      classSideDecl(clsBuilder);
    }
  }

  private void classSideDecl(final ClassBuilder clsBuilder)
      throws ParseError, ClassDefinitionError {
    clsBuilder.switchToClassSide();

    expect(Colon);
    expect(NewTerm);

    while (sym != EndTerm) {
      category(clsBuilder);
    }

    expect(EndTerm);
  }

  private void classHeader(final ClassBuilder clsBuilder)
      throws ParseError, ClassDefinitionError {
    expect(NewTerm);
    classComment(clsBuilder);

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
    if (sym != BeginComment) { return; }

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

  private void slotDeclarations(final ClassBuilder clsBuilder)
      throws ParseError, ClassDefinitionError {
    // Newspeak-speak: we do not support simSlotDecls, i.e.,
    //                 simultaneous slots clauses (spec 6.3.2)
    expect(Or);

    while (sym != Or) {
      slotDefinition(clsBuilder);
    }
    expect(Or);
  }

  private void slotDefinition(final ClassBuilder clsBuilder)
      throws ParseError, ClassDefinitionError {
    SourceCoordinate coord = getCoordinate();
    AccessModifier acccessModifier = accessModifier();

    String slotName = slotDecl();
    boolean immutable;
    ExpressionNode init;

    if (accept(Equal)) {
      immutable = true;
      init = expression(clsBuilder.getInitializerMethodBuilder());
      expect(Period);
    } else if (accept(SlotMutableAssign)) {
      immutable = false;
      init = expression(clsBuilder.getInitializerMethodBuilder());
      expect(Period);
    } else {
      immutable = false;
      init = null;
    }
    clsBuilder.addSlot(symbolFor(slotName), acccessModifier, immutable, init,
        getSource(coord));
  }

  private AccessModifier accessModifier() {
    if (sym == Identifier) {
      if (acceptIdentifier("private"))   { return AccessModifier.PRIVATE;   }
      if (acceptIdentifier("protected")) { return AccessModifier.PROTECTED; }
      if (acceptIdentifier("public"))    { return AccessModifier.PUBLIC;    }
    }
    return AccessModifier.PROTECTED;
  }

  private String slotDecl() throws ParseError {
    return identifier();
  }

  private void initExprs(final ClassBuilder clsBuilder)
      throws ParseError, ClassDefinitionError {
    MethodBuilder initializer = clsBuilder.getInitializerMethodBuilder();
    clsBuilder.addInitializerExpression(expression(initializer));

    while (accept(Period)) {
      if (sym != EndTerm) {
        clsBuilder.addInitializerExpression(expression(initializer));
      }
    }
  }

  private void sideDeclaration(final ClassBuilder clsBuilder)
      throws ParseError, ClassDefinitionError {
    expect(NewTerm);
    // TODO: this needs to be fixed, this needs to only accept a access modifier and class
    while (canAcceptIdentifierWithOptionalEarlierIdentifier(
        new String[]{"private", "protected", "public"}, "class")) {
      nestedClassDeclaration(clsBuilder);
    }

    while (sym != EndTerm) {
      category(clsBuilder);
    }

    expect(EndTerm);
  }

  private void nestedClassDeclaration(final ClassBuilder clsBuilder)
      throws ParseError, ClassDefinitionError {
    SourceCoordinate coord = getCoordinate();
    AccessModifier accessModifier = accessModifier();
    ClassBuilder nestedCls = new ClassBuilder(clsBuilder, accessModifier);
    classDeclaration(nestedCls);
    clsBuilder.addNestedClass(nestedCls.assemble(getSource(coord)));
  }

  private void category(final ClassBuilder clsBuilder)
      throws ParseError, ClassDefinitionError {
    String categoryName;
    // Newspeak-spec: this is not conform with Newspeak,
    //                as the category is normally not optional
    if (sym == STString) {
      categoryName = string();
    } else {
      categoryName = "";
    }
    while (sym != EndTerm && sym != STString) {
      comment();
      methodDeclaration(clsBuilder, symbolFor(categoryName));
      comment();
    }
  }

  private boolean symIn(final List<Symbol> ss) {
    return ss.contains(sym);
  }

  private boolean canAcceptIdentifierWithOptionalEarlierIdentifier(
      final String[] earlierIdentifier, final String identifier) {
    if (sym != Identifier) { return false; }

    if (identifier.equals(text)) { return true; }

    boolean oneMatches = false;
    for (String s : earlierIdentifier) {
      if (s.equals(text)) {
        oneMatches = true;
        break;
      }
    }

    if (!oneMatches) { return false; }

    peekForNextSymbolFromLexer();
    return nextSym == Identifier && identifier.equals(nextText);
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

  private void expectIdentifier(final String identifier, final String msg)
      throws ParseError {
    if (acceptIdentifier(identifier)) { return; }

    throw new ParseError(msg, Identifier, this);
  }

  private void expectIdentifier(final String identifier) throws ParseError {
    expectIdentifier(identifier, "Unexpected token. Expected '" + identifier +
        "', but found %(found)s");
  }

  private void expect(final Symbol s, final String msg) throws ParseError {
    if (accept(s)) { return; }

    throw new ParseError(msg, s, this);
  }

  private void expect(final Symbol s) throws ParseError {
    expect(s, "Unexpected symbol. Expected %(expected)s, but found %(found)s");
  }

  private boolean expectOneOf(final List<Symbol> ss) throws ParseError {
    if (acceptOneOf(ss)) { return true; }

    throw new ParseErrorWithSymbolList("Unexpected symbol. Expected one of " +
        "%(expected)s, but found %(found)s", ss, this);
  }

  SourceSection getSource(final SourceCoordinate coord) {
    assert lexer.getNumberOfCharactersRead() - coord.charIndex >= 0;
    return source.createSection("method", coord.startLine,
        coord.startColumn, coord.charIndex,
        lexer.getNumberOfCharactersRead() - coord.charIndex);
  }

  private void methodDeclaration(final ClassBuilder clsBuilder,
      final SSymbol category) throws ParseError, ClassDefinitionError {
    SourceCoordinate coord = getCoordinate();

    AccessModifier accessModifier = accessModifier();
    MethodBuilder builder = new MethodBuilder(
        clsBuilder, clsBuilder.getScopeForCurrentParserPosition());

    messagePattern(builder);
    expect(Equal, "Unexpected symbol %(found)s. Tried to parse method declaration and expect '=' between message pattern, and method body.");
    ExpressionNode body = methodBlock(builder);
    SMethod meth = builder.assemble(body, accessModifier, category, getSource(coord));
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

  private ExpressionNode methodBlock(final MethodBuilder builder)
      throws ParseError, ClassDefinitionError {
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
    } else {
      throw new ParseError("Unexpected symbol. Expected binary operator, "
          + "but found %(found)s", Symbol.NONE, this);
    }
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
    return identifier();
  }

  private ExpressionNode blockContents(final MethodBuilder builder)
      throws ParseError, ClassDefinitionError {
    comment();
    if (accept(Or)) {
      locals(builder);
      expect(Or);
    }
    return blockBody(builder);
  }

  private void locals(final MethodBuilder builder) throws ParseError {
    while (isIdentifier(sym)) {
      builder.addLocalIfAbsent(identifier());
    }
  }

  private ExpressionNode blockBody(final MethodBuilder builder)
      throws ParseError, ClassDefinitionError {
    SourceCoordinate coord = getCoordinate();
    List<ExpressionNode> expressions = new ArrayList<ExpressionNode>();

    while (true) {
      comment();

      if (accept(Exit)) {
        expressions.add(result(builder));
        return createSequence(expressions, getSource(coord));
      } else if (sym == EndBlock) {
        return createSequence(expressions, getSource(coord));
      } else if (sym == EndTerm) {
        // the end of the method has been found (EndTerm) - make it implicitly
        // return "self"

        // TODO: we might need something else to access self
        ExpressionNode self = implicitReceiverSend(builder, symbolFor("self"), getSource(getCoordinate()));
        expressions.add(self);
        return createSequence(expressions, getSource(coord));
      }

      expressions.add(expression(builder));
      accept(Period);
    }
  }

  private ExpressionNode result(final MethodBuilder builder)
      throws ParseError, ClassDefinitionError {
    SourceCoordinate coord = getCoordinate();

    ExpressionNode exp = expression(builder);
    accept(Period);

    if (builder.isBlockMethod()) {
      return builder.getNonLocalReturn(exp, getSource(coord));
    } else {
      return exp;
    }
  }

  private ExpressionNode expression(final MethodBuilder builder)
      throws ParseError, ClassDefinitionError {
    peekForNextSymbolFromLexer();

    if (nextSym == Assign) {
      return assignation(builder);
    } else {
      return evaluation(builder);
    }
  }

  private ExpressionNode assignation(final MethodBuilder builder)
      throws ParseError, ClassDefinitionError {
    return assignments(builder);
  }

  private ExpressionNode assignments(final MethodBuilder builder)
      throws ParseError, ClassDefinitionError {
    SourceCoordinate coord = getCoordinate();

    if (!isIdentifier(sym)) {
      throw new ParseError("Assignments should always target variables or" +
                           " fields, but found instead a %(found)s",
                           Symbol.Identifier, this);
    }
    SSymbol identifier = assignment();

    peekForNextSymbolFromLexer();

    ExpressionNode value;
    if (nextSym == Assign) {
      value = assignments(builder);
    } else {
      value = evaluation(builder);
    }

    return setterSend(builder, identifier, value, getSource(coord));
  }

  private SSymbol assignment() throws ParseError {
    SSymbol id = symbolFor(identifier());
    expect(Assign);
    return id;
  }

  private ExpressionNode evaluation(final MethodBuilder builder)
      throws ParseError, ClassDefinitionError {
    ExpressionNode exp;
    if (sym == Keyword) {
      // TODO: the receiver needs to be an implicit receiver!!!
      exp = keywordMessage(builder, builder.getSelfRead(null));
    } else {
      exp = primary(builder);
    }
    if (symIsMessageSend()) {
      exp = messages(builder, exp);
    }
    return exp;
  }

  private boolean symIsMessageSend() {
    return isIdentifier(sym) || sym == Keyword || sym == OperatorSequence
        || symIn(binaryOpSyms);
  }

  private ExpressionNode primary(final MethodBuilder builder)
      throws ParseError, ClassDefinitionError {
    switch (sym) {
      case Identifier: {
        SourceCoordinate coord = getCoordinate();
        // Parse true, false, and nil as keyword-like constructs
        // (cf. Newspeak spec on reserved words)
        if (acceptIdentifier("true")) {
          return new TrueLiteralNode(getSource(coord));
        }
        if (acceptIdentifier("false")) {
          return new FalseLiteralNode(getSource(coord));
        }
        if (acceptIdentifier("nil")) {
          return new NilLiteralNode(getSource(coord));
        }
        if ("outer".equals(text)) {
          return outerSend(builder);
        }

        SSymbol selector = unarySelector();
        return implicitReceiverSend(builder, selector, getSource(coord));
      }
      case NewTerm: {
        return nestedTerm(builder);
      }
      case NewBlock: {
        SourceCoordinate coord = getCoordinate();
        MethodBuilder bgenc = new MethodBuilder(builder);

        ExpressionNode blockBody = nestedBlock(bgenc);

        SMethod blockMethod = bgenc.assemble(blockBody,
            AccessModifier.BLOCK_METHOD, null, lastMethodsSourceSection);
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

  private ExpressionNode outerSend(final MethodBuilder builder)
      throws ParseError, ClassDefinitionError {
    SourceCoordinate coord = getCoordinate();
    expectIdentifier("outer");
    String outer = identifier();
    OuterObjectRead outerRcvr = builder.getOuterRead(outer, getSource(coord));
    if (symIsMessageSend()) {
      return messages(builder, outerRcvr);
    } else {
      return outerRcvr;
  }

  protected ExpressionNode binaryConsecutiveMessages(
      final MethodBuilder builder, ExpressionNode operand) throws ParseError,
      ClassDefinitionError {
    while (sym == OperatorSequence || symIn(binaryOpSyms)) {
      operand = binaryMessage(builder, operand);
    }
    return operand;
  }

  private ExpressionNode messages(final MethodBuilder builder,
      final ExpressionNode receiver) throws ParseError, ClassDefinitionError {
    ExpressionNode msg;
    if (isIdentifier(sym)) {
      msg = unaryMessage(receiver);

      while (isIdentifier(sym)) {
        msg = unaryMessage(msg);
      }

      msg = binaryConsecutiveMessages(builder, msg);

      if (sym == Keyword) {
        msg = keywordMessage(builder, msg);
      }
    } else if (sym == OperatorSequence || symIn(binaryOpSyms)) {
      msg = binaryConsecutiveMessages(builder, receiver);

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
      final ExpressionNode receiver) throws ParseError, ClassDefinitionError {
    SourceCoordinate coord = getCoordinate();
    SSymbol msg = binarySelector();
    ExpressionNode operand = binaryOperand(builder);

    return createMessageSend(msg, new ExpressionNode[] {receiver, operand},
        getSource(coord));
  }

  private ExpressionNode binaryOperand(final MethodBuilder builder)
      throws ParseError, ClassDefinitionError {
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
      final ExpressionNode receiver) throws ParseError, ClassDefinitionError {
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

  private ExpressionNode formula(final MethodBuilder builder)
      throws ParseError, ClassDefinitionError {
    ExpressionNode operand = binaryOperand(builder);

    operand = binaryConsecutiveMessages(builder, operand);
    return operand;
  }

  private ExpressionNode nestedTerm(final MethodBuilder builder)
      throws ParseError, ClassDefinitionError {
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

  private ExpressionNode nestedBlock(final MethodBuilder builder)
      throws ParseError, ClassDefinitionError {
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

  private ExpressionNode implicitReceiverSend(final MethodBuilder builder,
      final SSymbol selector, final SourceSection source) {
    // we need to handle super special here
    if ("super".equals(selector.getString())) {
      return builder.getSuperReadNode(source);
    }

    // first look up local or argument variables
    Variable variable = builder.getVariable(selector.getString());
    if (variable != null) {
      return builder.getReadNode(selector.getString(), source);
    }

    // otherwise, it is an implicit receiver send
    return SNodeFactory.createImplicitReceiverSend(selector,
        new ExpressionNode[] {builder.getSelfRead(null)},
        builder.getCurrentMethodScope(), builder.getHolder().getClassId(), source);
  }

  private ExpressionNode setterSend(final MethodBuilder builder,
      final SSymbol identifier, final ExpressionNode exp, final SourceSection source) {
    // write directly to local variables (excluding arguments)
    Local variable = builder.getLocal(identifier.getString());
    if (variable != null) {
      return builder.getWriteNode(identifier.getString(), exp, source);
    }

    // otherwise, it is a setter send.
    return SNodeFactory.createImplicitReceiverSetterSend(identifier, exp, source);
  }

  private void getSymbolFromLexer() {
    sym  = lexer.getSym();
    text = lexer.getText();
  }

  private void peekForNextSymbolFromLexer() {
    Peek peek = lexer.peek();
    nextSym  = peek.nextSym;
    nextText = peek.nextText;
  }

  private static boolean isIdentifier(final Symbol sym) {
    return sym == Identifier;
  }

  private static boolean printableSymbol(final Symbol sym) {
    return sym == Integer || sym == Double || sym.compareTo(STString) >= 0;
  }
}
