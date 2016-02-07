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
import static som.compiler.Symbol.EventualSend;
import static som.compiler.Symbol.Exit;
import static som.compiler.Symbol.Identifier;
import static som.compiler.Symbol.Integer;
import static som.compiler.Symbol.Keyword;
import static som.compiler.Symbol.KeywordSequence;
import static som.compiler.Symbol.Less;
import static som.compiler.Symbol.Minus;
import static som.compiler.Symbol.MixinOperator;
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
import static som.compiler.Tags.CONTROL_FLOW_CONDITION;
import static som.compiler.Tags.UNSPECIFIED_INVOKE;
import static som.interpreter.SNodeFactory.createImplicitReceiverSend;
import static som.interpreter.SNodeFactory.createMessageSend;
import static som.interpreter.SNodeFactory.createSequence;
import static som.vm.Symbols.symbolFor;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import som.compiler.Lexer.Peek;
import som.compiler.Lexer.SourceCoordinate;
import som.compiler.MixinBuilder.MixinDefinitionError;
import som.compiler.Variable.Local;
import som.interpreter.SNodeFactory;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractUninitializedMessageSendNode;
import som.interpreter.nodes.OuterObjectRead;
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
import som.interpreter.nodes.specialized.IntDownToDoInlinedLiteralsNodeGen;
import som.interpreter.nodes.specialized.IntTimesRepeatLiteralNodeGen;
import som.interpreter.nodes.specialized.IntToDoInlinedLiteralsNodeGen;
import som.interpreter.nodes.specialized.whileloops.WhileInlinedLiteralsNode;
import som.vm.Symbols;
import som.vmobjects.SInvokable;
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
  private final Set<SourceSection>  syntaxAnnotations;

  private static final Symbol[] singleOpSyms = new Symbol[] {Not, And, Or, Star,
    Div, Mod, Plus, Equal, More, Less, Comma, At, Per, NONE};

  private static final Symbol[] binaryOpSyms = new Symbol[] {Or, Comma, Minus,
    Equal, Not, And, Or, Star, Div, Mod, Plus, Equal, More, Less, Comma, At,
    Per, NONE};

  private static final Symbol[] keywordSelectorSyms = new Symbol[] {Keyword,
    KeywordSequence};

  private static final Symbol[] literalSyms = new Symbol[] {Pound, STString,
    Integer, Double, Minus};

  private static boolean arrayContains(final Symbol[] arr, final Symbol sym) {
    for (Symbol s : arr) {
      if (s == sym) {
        return true;
      }
    }
    return false;
  }

  public static MixinDefinition parseModule(final Source source) throws ParseError, MixinDefinitionError {
    Parser parser = new Parser(source.getReader(), source.getLength(), source);
    SourceCoordinate coord = parser.getCoordinate();
    MixinBuilder moduleBuilder = parser.moduleDeclaration();
    return moduleBuilder.assemble(parser.getSource(coord));
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

  public static class ParseErrorWithSymbols extends ParseError {
    private static final long serialVersionUID = 561313162441723955L;
    private final Symbol[] expectedSymbols;

    ParseErrorWithSymbols(final String message, final Symbol[] expected,
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

    this.syntaxAnnotations = new HashSet<>();
  }

  Set<SourceSection> getSyntaxAnnotations() {
    return syntaxAnnotations;
  }

  SourceCoordinate getCoordinate() {
    return lexer.getStartCoordinate();
  }

  public MixinBuilder moduleDeclaration() throws ParseError, MixinDefinitionError {
    comment();
    return classDeclaration(null, AccessModifier.PUBLIC);
  }

  private MixinBuilder classDeclaration(final MixinBuilder outerBuilder,
      final AccessModifier accessModifier) throws ParseError, MixinDefinitionError {
    expectIdentifier("class", "Found unexpected token %(found)s. " +
      "Tried parsing a class declaration and expected 'class' instead.", Tags.SYNTAX_KEYWORD);
    String mixinName = text;
    expect(Identifier, "Expected valid class name, but found %(found)s", Tags.SYNTAX_IDENTIFIER);

    MixinBuilder mxnBuilder = new MixinBuilder(outerBuilder, accessModifier, symbolFor(mixinName));

    MethodBuilder primaryFactory = mxnBuilder.getPrimaryFactoryMethodBuilder();

    SourceSection sourceSect;
    // Newspeak-spec: this is not strictly sufficient for Newspeak
    //                it could also parse a binary selector here, I think
    //                but, doesn't seem so useful, so, let's keep it simple
    if (sym == Identifier || sym == Keyword) {
      SourceCoordinate coord = getCoordinate();
      messagePattern(primaryFactory);
      sourceSect = getSource(coord);
    } else {
      // in the standard case, the primary factory method is #new
      primaryFactory.addArgumentIfAbsent("self", getEmptySource(Tags.SYNTAX_ARGUMENT));
      primaryFactory.setSignature(Symbols.NEW);
      sourceSect = getEmptySource();
    }
    mxnBuilder.setupInitializerBasedOnPrimaryFactory(sourceSect);

    expect(Equal, "Unexpected symbol %(found)s."
        + " Tried to parse the class declaration of " + mixinName
        + " and expect '=' before the (optional) inheritance declaration.");

    inheritanceListAndOrBody(mxnBuilder);
    return mxnBuilder;
  }

  private void inheritanceListAndOrBody(final MixinBuilder mxnBuilder)
      throws ParseError, MixinDefinitionError {
    if (sym == NewTerm) {
      defaultSuperclassAndBody(mxnBuilder);
    } else {
      explicitInheritanceListAndOrBody(mxnBuilder);
    }
  }

  private void defaultSuperclassAndBody(final MixinBuilder mxnBuilder)
      throws ParseError, MixinDefinitionError {
    MethodBuilder def = mxnBuilder.getClassInstantiationMethodBuilder();
    ExpressionNode selfRead = def.getSelfRead(null);
    ExpressionNode superClass = createMessageSend(Symbols.OBJECT,
        new ExpressionNode[] {selfRead}, false, getEmptySource(UNSPECIFIED_INVOKE));
    mxnBuilder.setSuperClassResolution(superClass);

    mxnBuilder.setSuperclassFactorySend(
        mxnBuilder.createStandardSuperFactorySend(
            getEmptySource(UNSPECIFIED_INVOKE)), true);

    classBody(mxnBuilder);
  }

  private void explicitInheritanceListAndOrBody(final MixinBuilder mxnBuilder)
      throws ParseError, MixinDefinitionError {
    SourceCoordinate superAndMixinCoord = getCoordinate();
    inheritanceClause(mxnBuilder);

    final boolean hasMixins = sym == MixinOperator;

    int i = 0;
    while (sym == MixinOperator) {
      i++;
      mixinApplication(mxnBuilder, i);
    }

    if (hasMixins) {
      mxnBuilder.setMixinResolverSource(getSource(superAndMixinCoord));
      SourceCoordinate initCoord = getCoordinate();
      if (accept(Period)) {
        // TODO: what else do we need to do here?
        mxnBuilder.setInitializerSource(getSource(initCoord));
        return;
      }
    }
    classBody(mxnBuilder);
  }

  private void mixinApplication(final MixinBuilder mxnBuilder, final int mixinId)
      throws ParseError, MixinDefinitionError {
    expect(MixinOperator);
    SourceCoordinate coord = getCoordinate();

    ExpressionNode mixinResolution = inheritancePrefixAndSuperclass(mxnBuilder);
    mxnBuilder.addMixinResolver(mixinResolution);

    AbstractUninitializedMessageSendNode mixinFactorySend;
    SSymbol uniqueInitName;
    if (sym != NewTerm && sym != MixinOperator && sym != Period) {
      mixinFactorySend = (AbstractUninitializedMessageSendNode) messages(
          mxnBuilder.getInitializerMethodBuilder(),
          mxnBuilder.getInitializerMethodBuilder().getSelfRead(null));

      uniqueInitName = MixinBuilder.getInitializerName(
          mixinFactorySend.getSelector(), mixinId);
      mixinFactorySend = (AbstractUninitializedMessageSendNode) MessageSendNode.adaptSymbol(
          uniqueInitName, mixinFactorySend);
    } else {
      uniqueInitName = MixinBuilder.getInitializerName(Symbols.NEW, mixinId);
      mixinFactorySend = (AbstractUninitializedMessageSendNode)
          SNodeFactory.createMessageSend(uniqueInitName,
              new ExpressionNode[] {mxnBuilder.getInitializerMethodBuilder().getSelfRead(null)},
              false, getSource(coord, UNSPECIFIED_INVOKE));
    }

    mxnBuilder.addMixinFactorySend(mixinFactorySend);
  }

  private void inheritanceClause(final MixinBuilder mxnBuilder)
      throws ParseError, MixinDefinitionError {
    ExpressionNode superClassResolution = inheritancePrefixAndSuperclass(mxnBuilder);
    mxnBuilder.setSuperClassResolution(superClassResolution);

    if (sym != NewTerm && sym != MixinOperator) {
      // This factory method on the super class is actually called as
      // initializer of the super class after object creation.
      // The Newspeak spec isn't entirely straight forward on that, but it says
      // that it is a runtime error if it is not the primary factory method.
      // Which for me implies that this one is special. And indeed, it is
      // used to create the proper initialize method, on which we rely here.
      ExpressionNode superFactorySend = messages(
          mxnBuilder.getInitializerMethodBuilder(),
          mxnBuilder.getInitializerMethodBuilder().getSuperReadNode(null));

      SSymbol initializerName = MixinBuilder.getInitializerName(
          ((AbstractUninitializedMessageSendNode) superFactorySend).getSelector());

      // TODO: the false we pass here, should that be conditional on the superFactorSend being a #new send?
      mxnBuilder.setSuperclassFactorySend(
          MessageSendNode.adaptSymbol(
              initializerName,
              (AbstractUninitializedMessageSendNode) superFactorySend), false);
    } else {
      mxnBuilder.setSuperclassFactorySend(
          mxnBuilder.createStandardSuperFactorySend(
              getEmptySource(UNSPECIFIED_INVOKE)), true);
    }
  }

  private ExpressionNode inheritancePrefixAndSuperclass(
      final MixinBuilder mxnBuilder) throws ParseError, MixinDefinitionError {
    MethodBuilder meth = mxnBuilder.getClassInstantiationMethodBuilder();
    SourceCoordinate coord = getCoordinate();

    if (acceptIdentifier("outer", Tags.SYNTAX_KEYWORD)) {
      String outer = identifier();
      OuterObjectRead self = meth.getOuterRead(outer, getSource(coord));
      if (sym == Identifier) {
        return unaryMessage(self, false);
      } else {
        return self;
      }
    }

    ExpressionNode self;
    if (acceptIdentifier("super", Tags.SYNTAX_KEYWORD)) {
      self = meth.getSuperReadNode(getSource(coord));
    } else if (acceptIdentifier("self", Tags.SYNTAX_KEYWORD)) {
      self = meth.getSelfRead(getSource(coord));
    } else {
      return meth.getImplicitReceiverSend(unarySelector(), coord, this);
    }
    return unaryMessage(self, false);
  }

  private void classBody(final MixinBuilder mxnBuilder)
      throws ParseError, MixinDefinitionError {
    classHeader(mxnBuilder);
    sideDeclaration(mxnBuilder);
    if (sym == Colon) {
      classSideDecl(mxnBuilder);
    }
  }

  private void classSideDecl(final MixinBuilder mxnBuilder)
      throws ParseError, MixinDefinitionError {
    mxnBuilder.switchToClassSide();

    expect(Colon);
    expect(NewTerm);

    while (sym != EndTerm) {
      category(mxnBuilder);
    }

    expect(EndTerm);
  }

  private void classHeader(final MixinBuilder mxnBuilder)
      throws ParseError, MixinDefinitionError {
    expect(NewTerm);
    classComment(mxnBuilder);

    boolean empty = true;
    SourceCoordinate coord = getCoordinate();
    if (sym == Or) {
      slotDeclarations(mxnBuilder);
      empty = false;
    }

    if (sym != EndTerm) {
      initExprs(mxnBuilder);
      empty = false;
    }

    SourceSection sourceSect = empty ? getEmptySource() : getSource(coord);
    mxnBuilder.setInitializerSource(sourceSect);
    expect(EndTerm);
  }

  private void classComment(final MixinBuilder mxnBuilder) throws ParseError {
    mxnBuilder.setComment(comment());
  }

  private String comment() throws ParseError {
    if (sym != BeginComment) { return ""; }

    SourceCoordinate coord = getCoordinate();

    expect(BeginComment);

    String comment = "";
    while (sym != EndComment) {
      comment += lexer.getCommentPart();
      getSymbolFromLexer();

      if (sym == BeginComment) {
        comment += "(*" + comment() + "*)";
      }
    }
    expect(EndComment);
    syntaxAnnotations.add(getSource(coord, Tags.SYNTAX_COMMENT));
    return comment;
  }

  private void slotDeclarations(final MixinBuilder mxnBuilder)
      throws ParseError, MixinDefinitionError {
    // Newspeak-speak: we do not support simSlotDecls, i.e.,
    //                 simultaneous slots clauses (spec 6.3.2)
    expect(Or);

    while (sym != Or) {
      slotDefinition(mxnBuilder);
    }

    comment();

    expect(Or);
  }

  private void slotDefinition(final MixinBuilder mxnBuilder)
      throws ParseError, MixinDefinitionError {
    comment();
    if (sym == Or) { return; }

    SourceCoordinate coord = getCoordinate();
    AccessModifier acccessModifier = accessModifier();

    String slotName = slotDecl();
    boolean immutable;
    ExpressionNode init;

    if (accept(Equal)) {
      immutable = true;
      init = expression(mxnBuilder.getInitializerMethodBuilder());
      expect(Period);
    } else if (accept(SlotMutableAssign)) {
      immutable = false;
      init = expression(mxnBuilder.getInitializerMethodBuilder());
      expect(Period);
    } else {
      immutable = false;
      init = null;
    }
    mxnBuilder.addSlot(symbolFor(slotName), acccessModifier, immutable, init,
        getSource(coord));
  }

  private AccessModifier accessModifier() {
    if (sym == Identifier) {
      if (acceptIdentifier("private",   Tags.SYNTAX_KEYWORD)) { return AccessModifier.PRIVATE;   }
      if (acceptIdentifier("protected", Tags.SYNTAX_KEYWORD)) { return AccessModifier.PROTECTED; }
      if (acceptIdentifier("public",    Tags.SYNTAX_KEYWORD)) { return AccessModifier.PUBLIC;    }
    }
    return AccessModifier.PROTECTED;
  }

  private String slotDecl() throws ParseError {
    return identifier();
  }

  private void initExprs(final MixinBuilder mxnBuilder)
      throws ParseError, MixinDefinitionError {
    MethodBuilder initializer = mxnBuilder.getInitializerMethodBuilder();
    mxnBuilder.addInitializerExpression(expression(initializer));

    while (accept(Period)) {
      if (sym != EndTerm) {
        mxnBuilder.addInitializerExpression(expression(initializer));
      }
    }
  }

  private void sideDeclaration(final MixinBuilder mxnBuilder)
      throws ParseError, MixinDefinitionError {
    expect(NewTerm);
    comment();

    while (canAcceptIdentifierWithOptionalEarlierIdentifier(
        new String[]{"private", "protected", "public"}, "class")) {
      nestedClassDeclaration(mxnBuilder);
      comment();
    }

    while (sym != EndTerm) {
      category(mxnBuilder);
    }

    expect(EndTerm);
  }

  private void nestedClassDeclaration(final MixinBuilder mxnBuilder)
      throws ParseError, MixinDefinitionError {
    SourceCoordinate coord = getCoordinate();
    AccessModifier accessModifier = accessModifier();
    MixinBuilder nestedCls = classDeclaration(mxnBuilder, accessModifier);
    mxnBuilder.addNestedMixin(nestedCls.assemble(getSource(coord)));
  }

  private void category(final MixinBuilder mxnBuilder)
      throws ParseError, MixinDefinitionError {
    String categoryName;
    // Newspeak-spec: this is not conform with Newspeak,
    //                as the category is normally not optional
    if (sym == STString) {
      SourceCoordinate coord = getCoordinate();
      categoryName = string();
      syntaxAnnotations.add(getSource(coord, Tags.SYNTAX_COMMENT));
    } else {
      categoryName = "";
    }
    while (sym != EndTerm && sym != STString) {
      comment();
      methodDeclaration(mxnBuilder, symbolFor(categoryName));
      comment();
    }
  }

  private boolean symIn(final Symbol[] ss) {
    return arrayContains(ss, sym);
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

  private boolean acceptIdentifier(final String identifier, final String... tags) {
    if (sym == Identifier && identifier.equals(text)) {
      accept(Identifier, tags);
      return true;
    }
    return false;
  }

  private boolean accept(final Symbol s, final String... tags) {
    SourceCoordinate coord = getCoordinate();
    if (sym == s) {
      getSymbolFromLexer();
      if (tags.length > 0) {
        syntaxAnnotations.add(getSource(coord, tags));
      }
      return true;
    }
    return false;
  }

  private boolean acceptOneOf(final Symbol[] ss) {
    if (symIn(ss)) {
      getSymbolFromLexer();
      return true;
    }
    return false;
  }

  private void expectIdentifier(final String identifier, final String msg, final String... tags)
      throws ParseError {
    if (acceptIdentifier(identifier, tags)) { return; }

    throw new ParseError(msg, Identifier, this);
  }

  private void expect(final Symbol s, final String msg, final String... tags) throws ParseError {
    if (accept(s, tags)) {
      return;
    }

    throw new ParseError(msg, s, this);
  }

  private void expect(final Symbol s) throws ParseError {
    expect(s, "Unexpected symbol. Expected %(expected)s, but found %(found)s");
  }

  private boolean expectOneOf(final Symbol[] ss) throws ParseError {
    if (acceptOneOf(ss)) { return true; }

    throw new ParseErrorWithSymbols("Unexpected symbol. Expected one of " +
        "%(expected)s, but found %(found)s", ss, this);
  }

  SourceSection getEmptySource(final String... tags) {
    SourceCoordinate coord = getCoordinate();
    return source.createSection("method", coord.startLine, coord.startColumn,
        coord.charIndex, 0, tags);
  }

  SourceSection getSource(final SourceCoordinate coord, final String... tags) {
    assert lexer.getNumberOfCharactersRead() - coord.charIndex >= 0;
    return source.createSection("method", coord.startLine,
        coord.startColumn, coord.charIndex,
        lexer.getNumberOfCharactersRead() - coord.charIndex, tags);
  }

  private void methodDeclaration(final MixinBuilder mxnBuilder,
      final SSymbol category) throws ParseError, MixinDefinitionError {
    SourceCoordinate coord = getCoordinate();

    AccessModifier accessModifier = accessModifier();
    MethodBuilder builder = new MethodBuilder(
        mxnBuilder, mxnBuilder.getScopeForCurrentParserPosition());

    messagePattern(builder);
    expect(Equal, "Unexpected symbol %(found)s. Tried to parse method declaration and expect '=' between message pattern, and method body.");
    ExpressionNode body = methodBlock(builder);
    SInvokable meth = builder.assemble(body, accessModifier, category, getSource(coord));
    mxnBuilder.addMethod(meth);
  }

  private void messagePattern(final MethodBuilder builder) throws ParseError {
    builder.addArgumentIfAbsent("self", getEmptySource(Tags.SYNTAX_ARGUMENT));
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

    SourceCoordinate coord = getCoordinate();
    builder.addArgumentIfAbsent(argument(), getSource(coord, Tags.SYNTAX_ARGUMENT));
  }

  private void keywordPattern(final MethodBuilder builder) throws ParseError {
    StringBuilder kw = new StringBuilder();
    do {
      kw.append(keyword());
      SourceCoordinate coord = getCoordinate();
      builder.addArgumentIfAbsent(argument(), getSource(coord, Tags.SYNTAX_ARGUMENT));
    }
    while (sym == Keyword);

    builder.setSignature(symbolFor(kw.toString()));
  }

  private ExpressionNode methodBlock(final MethodBuilder builder)
      throws ParseError, MixinDefinitionError {
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
    SourceCoordinate coord = getCoordinate();
    String id = identifier();
    syntaxAnnotations.add(getSource(coord, Tags.SYNTAX_ARGUMENT));
    return id;
  }

  private ExpressionNode blockContents(final MethodBuilder builder)
      throws ParseError, MixinDefinitionError {
    comment();
    if (accept(Or)) {
      locals(builder);
      expect(Or);
    }
    return blockBody(builder);
  }

  private void locals(final MethodBuilder builder) throws ParseError {
    while (sym == Identifier) {
      SourceCoordinate coord = getCoordinate();
      String id = identifier();
      SourceSection source = getSource(coord, Tags.SYNTAX_LOCAL_VARIABLE);
      builder.addLocalIfAbsent(id, source);
      syntaxAnnotations.add(source);
    }
  }

  private ExpressionNode blockBody(final MethodBuilder builder)
      throws ParseError, MixinDefinitionError {
    SourceCoordinate coord = getCoordinate();
    List<ExpressionNode> expressions = new ArrayList<ExpressionNode>();

    while (true) {
      comment();

      if (accept(Exit, Tags.SYNTAX_KEYWORD)) {
        expressions.add(result(builder));
        return createSequence(expressions, getSource(coord));
      } else if (sym == EndBlock) {
        return createSequence(expressions, getSource(coord));
      } else if (sym == EndTerm) {
        // the end of the method has been found (EndTerm) - make it implicitly
        // return "self"
        ExpressionNode self = builder.getSelfRead(getEmptySource());
        expressions.add(self);
        return createSequence(expressions, getSource(coord));
      }

      expressions.add(expression(builder));
      accept(Period);
    }
  }

  private ExpressionNode result(final MethodBuilder builder)
      throws ParseError, MixinDefinitionError {
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
      throws ParseError, MixinDefinitionError {
    comment();
    peekForNextSymbolFromLexer();

    if (nextSym == Assign) {
      return assignation(builder);
    } else {
      return evaluation(builder);
    }
  }

  private ExpressionNode assignation(final MethodBuilder builder)
      throws ParseError, MixinDefinitionError {
    return assignments(builder);
  }

  private ExpressionNode assignments(final MethodBuilder builder)
      throws ParseError, MixinDefinitionError {
    SourceCoordinate coord = getCoordinate();

    if (sym != Identifier) {
      throw new ParseError("Assignments should always target variables or" +
                           " fields, but found instead a %(found)s",
                           Identifier, this);
    }
    SSymbol identifier = assignment();

    peekForNextSymbolFromLexer();

    ExpressionNode value;
    if (nextSym == Assign) {
      value = assignments(builder);
    } else {
      value = evaluation(builder);
    }

    return builder.getSetterSend(identifier, value, getSource(coord));
  }

  private SSymbol assignment() throws ParseError {
    SSymbol id = symbolFor(identifier());
    expect(Assign);
    return id;
  }

  private ExpressionNode evaluation(final MethodBuilder builder)
      throws ParseError, MixinDefinitionError {
    ExpressionNode exp;
    if (sym == Keyword) {
      exp = keywordMessage(builder, builder.getSelfRead(null), false, false);
    } else {
      exp = primary(builder);
    }
    if (symIsMessageSend()) {
      exp = messages(builder, exp);
    }
    return exp;
  }

  private boolean symIsMessageSend() {
    return sym == Identifier || sym == Keyword || sym == OperatorSequence
        || symIn(binaryOpSyms) || sym == EventualSend;
  }

  private ExpressionNode primary(final MethodBuilder builder)
      throws ParseError, MixinDefinitionError {
    switch (sym) {
      case Identifier: {
        SourceCoordinate coord = getCoordinate();
        // Parse true, false, and nil as keyword-like constructs
        // (cf. Newspeak spec on reserved words)
        if (acceptIdentifier("true", Tags.SYNTAX_LITERAL)) {
          return new TrueLiteralNode(getSource(coord));
        }
        if (acceptIdentifier("false", Tags.SYNTAX_LITERAL)) {
          return new FalseLiteralNode(getSource(coord));
        }
        if (acceptIdentifier("nil", Tags.SYNTAX_LITERAL)) {
          return new NilLiteralNode(getSource(coord));
        }
        if ("outer".equals(text)) {
          return outerSend(builder);
        }

        SSymbol selector = unarySelector();
        return builder.getImplicitReceiverSend(selector, coord, this);
      }
      case NewTerm: {
        return nestedTerm(builder);
      }
      case NewBlock: {
        SourceCoordinate coord = getCoordinate();
        MethodBuilder bgenc = new MethodBuilder(builder);

        ExpressionNode blockBody = nestedBlock(bgenc);

        SInvokable blockMethod = bgenc.assemble(blockBody,
            AccessModifier.BLOCK_METHOD, null, lastMethodsSourceSection);
        builder.addEmbeddedBlockMethod(blockMethod);

        if (bgenc.requiresContext()) {
          return new BlockNodeWithContext(blockMethod, getSource(coord));
        } else {
          return new BlockNode(blockMethod, getSource(coord));
        }
      }
      default: {
        if (symIn(literalSyms)) {
          return literal();
        }
      }
    }
    throw new ParseError("Unexpected symbol. Tried to parse a primary "
        + "expression but found %(found)s", sym, this);
  }

  private ExpressionNode outerSend(final MethodBuilder builder)
      throws ParseError, MixinDefinitionError {
    SourceCoordinate coord = getCoordinate();
    expectIdentifier("outer",
        "Unexpected token. Expected 'outer' keyword, but found %(found)s",
        Tags.SYNTAX_KEYWORD);

    String outer = identifier();

    ExpressionNode operand = builder.getOuterRead(outer, getSource(coord));
    operand = binaryConsecutiveMessages(builder, operand, false);
    return operand;
  }

  protected ExpressionNode binaryConsecutiveMessages(
      final MethodBuilder builder, ExpressionNode operand,
      boolean eventualSend) throws ParseError,
      MixinDefinitionError {
    while (sym == OperatorSequence || symIn(binaryOpSyms)) {
      operand = binaryMessage(builder, operand, eventualSend);
      eventualSend = accept(EventualSend, Tags.SYNTAX_KEYWORD);
    }
    return operand;
  }

  private ExpressionNode messages(final MethodBuilder builder,
      final ExpressionNode receiver) throws ParseError, MixinDefinitionError {
    ExpressionNode msg;
    boolean evenutalSend = accept(EventualSend, Tags.SYNTAX_KEYWORD);

    if (sym == Identifier) {
      msg = unaryMessage(receiver, evenutalSend);
      evenutalSend = accept(EventualSend, Tags.SYNTAX_KEYWORD);

      while (sym == Identifier) {
        msg = unaryMessage(msg, evenutalSend);
        evenutalSend = accept(EventualSend, Tags.SYNTAX_KEYWORD);
      }

      if (sym == OperatorSequence || symIn(binaryOpSyms)) {
        msg = binaryConsecutiveMessages(builder, msg, evenutalSend);
        evenutalSend = accept(EventualSend, Tags.SYNTAX_KEYWORD);
      }

      if (sym == Keyword) {
        msg = keywordMessage(builder, msg, true, evenutalSend);
      }
    } else if (sym == OperatorSequence || symIn(binaryOpSyms)) {
      msg = binaryConsecutiveMessages(builder, receiver, evenutalSend);
      evenutalSend = accept(EventualSend, Tags.SYNTAX_KEYWORD);

      if (sym == Keyword) {
        msg = keywordMessage(builder, msg, true, evenutalSend);
      }
    } else {
      msg = keywordMessage(builder, receiver, true, evenutalSend);
    }
    return msg;
  }

  private ExpressionNode unaryMessage(final ExpressionNode receiver,
      final boolean eventualSend) throws ParseError {
    SourceCoordinate coord = getCoordinate();
    SSymbol selector = unarySelector();
    return createMessageSend(selector, new ExpressionNode[] {receiver},
        eventualSend, getSource(coord, UNSPECIFIED_INVOKE));
  }

  private ExpressionNode tryInliningBinaryMessage(final MethodBuilder builder,
      final ExpressionNode receiver, final SourceCoordinate coord, final SSymbol msg,
      final ExpressionNode operand) {
    List<ExpressionNode> arguments = new ArrayList<ExpressionNode>();
    arguments.add(receiver);
    arguments.add(operand);
    SourceSection source = getSource(coord);
    ExpressionNode node = inlineControlStructureIfPossible(builder, arguments,
        msg.getString(), msg.getNumberOfSignatureArguments(), source);
    return node;
  }

  private ExpressionNode binaryMessage(final MethodBuilder builder,
      final ExpressionNode receiver, final boolean eventualSend)
          throws ParseError, MixinDefinitionError {
    SourceCoordinate coord = getCoordinate();
    SSymbol msg = binarySelector();
    ExpressionNode operand = binaryOperand(builder);

    if (!eventualSend) {
      ExpressionNode node = tryInliningBinaryMessage(builder, receiver, coord,
          msg, operand);
      if (node != null) {
        return node;
      }
    }
    return createMessageSend(msg, new ExpressionNode[] {receiver, operand},
        eventualSend, getSource(coord, UNSPECIFIED_INVOKE));
  }

  private ExpressionNode binaryOperand(final MethodBuilder builder)
      throws ParseError, MixinDefinitionError {
    ExpressionNode operand = primary(builder);

    // a binary operand can receive unaryMessages
    // Example: 2 * 3 asString
    //   is evaluated as 2 * (3 asString)
    boolean evenutalSend = accept(EventualSend, Tags.SYNTAX_KEYWORD);
    while (sym == Identifier) {
      operand = unaryMessage(operand, evenutalSend);
      evenutalSend = accept(EventualSend, Tags.SYNTAX_KEYWORD);
    }

    assert !evenutalSend : "eventualSend should not be true, because that means we steal it from the next operation (think here shouldn't be one, but still...)";
    return operand;
  }

  // TODO: if the eventual send is not consumed by an expression (assignment, etc)
  //       we don't need to create a promise

  private ExpressionNode keywordMessage(final MethodBuilder builder,
      final ExpressionNode receiver, final boolean explicitRcvr,
      final boolean eventualSend) throws ParseError, MixinDefinitionError {
    assert !(!explicitRcvr && eventualSend);
    SourceCoordinate coord = getCoordinate();
    List<ExpressionNode> arguments = new ArrayList<ExpressionNode>();
    StringBuilder        kw        = new StringBuilder();

    arguments.add(receiver);

    do {
      kw.append(keyword());
      arguments.add(formula(builder));
    }
    while (sym == Keyword);

    String msgStr = kw.toString();
    SSymbol msg = symbolFor(msgStr);

    if (!eventualSend) {
      ExpressionNode node = inlineControlStructureIfPossible(builder, arguments,
          msgStr, msg.getNumberOfSignatureArguments(), getSource(coord));
      if (node != null) {
        return node;
      }
    }

    SourceSection source = getSource(coord, UNSPECIFIED_INVOKE);
    ExpressionNode[] args = arguments.toArray(new ExpressionNode[0]);
    if (explicitRcvr) {
      return createMessageSend(msg, args, eventualSend, source);
    } else {
      assert !eventualSend;
      return createImplicitReceiverSend(msg, args,
          builder.getCurrentMethodScope(),
          builder.getEnclosingMixinBuilder().getMixinId(), source);
    }
  }

  protected ExpressionNode inlineControlStructureIfPossible(
      final MethodBuilder builder, final List<ExpressionNode> arguments,
      final String msgStr, final int numberOfArguments,
      final SourceSection source) {
    if (numberOfArguments == 2) {
      if (arguments.get(1) instanceof LiteralNode) {
        if ("ifTrue:".equals(msgStr)) {
          ExpressionNode condition = arguments.get(0);
          condition.addTagsToSourceSection(CONTROL_FLOW_CONDITION);
          ExpressionNode inlinedBody = ((LiteralNode) arguments.get(1)).inline(builder);
          return new IfInlinedLiteralNode(condition, true, inlinedBody,
              arguments.get(1), source);
        } else if ("ifFalse:".equals(msgStr)) {
          ExpressionNode condition = arguments.get(0);
          condition.addTagsToSourceSection(CONTROL_FLOW_CONDITION);
          ExpressionNode inlinedBody = ((LiteralNode) arguments.get(1)).inline(builder);
          return new IfInlinedLiteralNode(condition, false, inlinedBody,
              arguments.get(1), source);
        } else if ("whileTrue:".equals(msgStr)) {
          ExpressionNode inlinedCondition = ((LiteralNode) arguments.get(0)).inline(builder);
          inlinedCondition.addTagsToSourceSection(CONTROL_FLOW_CONDITION);
          ExpressionNode inlinedBody      = ((LiteralNode) arguments.get(1)).inline(builder);
          return new WhileInlinedLiteralsNode(inlinedCondition, inlinedBody,
              true, arguments.get(0), arguments.get(1), source);
        } else if ("whileFalse:".equals(msgStr)) {
          ExpressionNode inlinedCondition = ((LiteralNode) arguments.get(0)).inline(builder);
          inlinedCondition.addTagsToSourceSection(CONTROL_FLOW_CONDITION);
          ExpressionNode inlinedBody      = ((LiteralNode) arguments.get(1)).inline(builder);
          return new WhileInlinedLiteralsNode(inlinedCondition, inlinedBody,
              false, arguments.get(0), arguments.get(1), source);
        } else if ("or:".equals(msgStr) || "||".equals(msgStr)) {
          ExpressionNode inlinedArg = ((LiteralNode) arguments.get(1)).inline(builder);
          return new OrInlinedLiteralNode(arguments.get(0), inlinedArg, arguments.get(1), source);
        } else if ("and:".equals(msgStr) || "&&".equals(msgStr)) {
          ExpressionNode inlinedArg = ((LiteralNode) arguments.get(1)).inline(builder);
          return new AndInlinedLiteralNode(arguments.get(0), inlinedArg, arguments.get(1), source);
        } else if ("timesRepeat:".equals(msgStr)) {
          ExpressionNode inlinedBody = ((LiteralNode) arguments.get(1)).inline(builder);
          return IntTimesRepeatLiteralNodeGen.create(inlinedBody,
              arguments.get(1), source, arguments.get(0));
        }
      }
    } else if (numberOfArguments == 3) {
      if ("ifTrue:ifFalse:".equals(msgStr) &&
          arguments.get(1) instanceof LiteralNode && arguments.get(2) instanceof LiteralNode) {
        ExpressionNode condition = arguments.get(0);
        condition.addTagsToSourceSection(CONTROL_FLOW_CONDITION);
        ExpressionNode inlinedTrueNode  = ((LiteralNode) arguments.get(1)).inline(builder);
        ExpressionNode inlinedFalseNode = ((LiteralNode) arguments.get(2)).inline(builder);
        return new IfTrueIfFalseInlinedLiteralsNode(condition,
            inlinedTrueNode, inlinedFalseNode, arguments.get(1), arguments.get(2),
            source);
      } else if ("to:do:".equals(msgStr) &&
          arguments.get(2) instanceof LiteralNode) {
        Local loopIdx = builder.addLocal("i:" + source.getCharIndex(), source);
        ExpressionNode inlinedBody = ((LiteralNode) arguments.get(2)).inline(builder, loopIdx);
        return IntToDoInlinedLiteralsNodeGen.create(inlinedBody, loopIdx.getSlot(), loopIdx.source,
            arguments.get(2), source, arguments.get(0), arguments.get(1));
      } else if ("downTo:do:".equals(msgStr) &&
          arguments.get(2) instanceof LiteralNode) {
        Local loopIdx = builder.addLocal("i:" + source.getCharIndex(), source);
        ExpressionNode inlinedBody = ((LiteralNode) arguments.get(2)).inline(builder, loopIdx);
        return IntDownToDoInlinedLiteralsNodeGen.create(inlinedBody, loopIdx.getSlot(), loopIdx.source,
            arguments.get(2), source, arguments.get(0), arguments.get(1));
      }
    }
    return null;
  }

  private ExpressionNode formula(final MethodBuilder builder)
      throws ParseError, MixinDefinitionError {
    ExpressionNode operand = binaryOperand(builder);
    boolean evenutalSend = accept(EventualSend);

    operand = binaryConsecutiveMessages(builder, operand, evenutalSend);
    return operand;
  }

  private ExpressionNode nestedTerm(final MethodBuilder builder)
      throws ParseError, MixinDefinitionError {
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

       SourceSection source = getSource(coord, Tags.SYNTAX_LITERAL);
       // TODO: add support for parsing big integers
       //  return new BigIntegerLiteralNode(BigInteger.valueOf(i), source);
       return new IntegerLiteralNode(i, source);
    } catch (NumberFormatException e) {
      throw new ParseError("Could not parse integer. Expected a number but " +
                           "got '" + text + "'", NONE, this);
    }
  }

  private LiteralNode literalDouble(final boolean isNegative, final SourceCoordinate coord) throws ParseError {
    try {
      String doubleText = text;
      expect(Double);
      double d = java.lang.Double.parseDouble(doubleText);
      if (isNegative) {
        d = 0.0 - d;
      }
      SourceSection source = getSource(coord, Tags.SYNTAX_LITERAL);
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

    return new SymbolLiteralNode(symb, getSource(coord, Tags.SYNTAX_LITERAL));
  }

  private LiteralNode literalString() throws ParseError {
    SourceCoordinate coord = getCoordinate();
    String s = string();

    return new StringLiteralNode(s, getSource(coord, Tags.SYNTAX_LITERAL));
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
      throws ParseError, MixinDefinitionError {
    expect(NewBlock);
    SourceCoordinate coord = getCoordinate();

    builder.addArgumentIfAbsent("$blockSelf", getSource(coord, Tags.SYNTAX_ARGUMENT));

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
      SourceCoordinate coord = getCoordinate();
      builder.addArgumentIfAbsent(argument(), getSource(coord, Tags.SYNTAX_ARGUMENT));
    }
    while (sym == Colon);
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

  private static boolean printableSymbol(final Symbol sym) {
    return sym == Integer || sym == Double || sym.compareTo(STString) >= 0;
  }
}
