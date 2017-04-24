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
import static som.compiler.Symbol.EndLiteralArray;
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
import static som.compiler.Symbol.NewLiteralArray;
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
import static som.interpreter.SNodeFactory.createImplicitReceiverSend;
import static som.interpreter.SNodeFactory.createLiteralArray;
import static som.interpreter.SNodeFactory.createMessageSend;
import static som.interpreter.SNodeFactory.createSequence;
import static som.vm.Symbols.symbolFor;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.Lexer.Peek;
import som.compiler.MethodBuilder.MethodDefinitionError;
import som.compiler.MixinBuilder.MixinDefinitionError;
import som.compiler.Variable.Argument;
import som.compiler.Variable.Local;
import som.interpreter.SomLanguage;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractUninitializedMessageSendNode;
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
import som.vm.VmSettings;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;
import tools.SourceCoordinate;
import tools.debugger.Tags;
import tools.debugger.Tags.ArgumentTag;
import tools.debugger.Tags.CommentTag;
import tools.debugger.Tags.DelimiterClosingTag;
import tools.debugger.Tags.DelimiterOpeningTag;
import tools.debugger.Tags.IdentifierTag;
import tools.debugger.Tags.KeywordTag;
import tools.debugger.Tags.LiteralTag;
import tools.debugger.Tags.LocalVariableTag;
import tools.debugger.Tags.StatementSeparatorTag;
import tools.language.StructuralProbe;


public class Parser {

  private final Lexer               lexer;
  private final Source              source;

  private final SomLanguage         language;

  private Symbol                    sym;
  private String                    text;
  private Symbol                    nextSym;
  private String                    nextText;

  private SourceSection             lastMethodsSourceSection;
  private final Set<SourceSection>  syntaxAnnotations;
  private final StructuralProbe     structuralProbe;

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

  @Override
  public String toString() {
    return "Parser(" + source.getName() + ", " + this.getCoordinate().toString() + ")";
  }

  public static class ParseError extends ProgramDefinitionError {
    private static final long serialVersionUID = 425390202979033628L;
    private final SourceCoordinate sourceCoordinate;
    private final String text;
    private final String rawBuffer;
    private final String fileName;
    private final Symbol expected;
    private final Symbol found;

    ParseError(final String message, final Symbol expected, final Parser parser) {
      super(message);
      if (parser.lexer == null) {
        this.sourceCoordinate = new SourceCoordinate(0, 0, 0, 0);
        this.rawBuffer        = "";
      } else {
        this.sourceCoordinate = parser.getCoordinate();
        this.rawBuffer        = parser.lexer.getRawBuffer();
      }
      this.text             = parser.text;
      this.fileName         = parser.source.getName();
      this.expected         = expected;
      this.found            = parser.sym;
    }

    protected String expectedSymbolAsString() {
      return expected.toString();
    }

    public SourceCoordinate getSourceCoordinate() {
      return sourceCoordinate;
    }

    @Override
    public String getMessage() {
      String msg = super.getMessage();

      String foundStr;
      if (Parser.printableSymbol(found)) {
        foundStr = found + " (" + text + ")";
      } else {
        foundStr = found.toString();
      }
      String expectedStr = expectedSymbolAsString();

      msg = msg.replace("%(expected)s", expectedStr);
      msg = msg.replace("%(found)s",    foundStr);

      return msg;
    }

    @Override
    public String toString() {
      String msg = "%(file)s:%(line)d:%(column)d: error: " + super.getMessage();
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

  public Parser(final Reader reader, final long fileSize, final Source source,
      final StructuralProbe structuralProbe, final SomLanguage language) throws ParseError {
    this.source = source;
    this.language = language;

    sym = NONE;
    nextSym = NONE;

    if (fileSize == 0) {
      throw new ParseError("Provided file is empty.", NONE, this);
    }

    lexer = new Lexer(reader, fileSize);

    getSymbolFromLexer();

    this.syntaxAnnotations = new HashSet<>();
    this.structuralProbe = structuralProbe;
  }

  Set<SourceSection> getSyntaxAnnotations() {
    return syntaxAnnotations;
  }

  public SourceCoordinate getCoordinate() {
    return lexer.getStartCoordinate();
  }

  public MixinBuilder moduleDeclaration() throws ProgramDefinitionError {
    comments();
    return classDeclaration(null, AccessModifier.PUBLIC);
  }

  protected String className() throws ParseError {
    String mixinName = text;
    expect(Identifier, IdentifierTag.class);
    return mixinName;
  }

  private MixinBuilder classDeclaration(final MixinBuilder outerBuilder,
      final AccessModifier accessModifier) throws ProgramDefinitionError {
    expectIdentifier("class", "Found unexpected token %(found)s. " +
      "Tried parsing a class declaration and expected 'class' instead.",
      KeywordTag.class);

    SourceCoordinate coord = getCoordinate();
    String mixinName = className();
    SourceSection nameSS = getSource(coord);

    MixinBuilder mxnBuilder = new MixinBuilder(outerBuilder, accessModifier,
        symbolFor(mixinName), nameSS, structuralProbe, language);

    MethodBuilder primaryFactory = mxnBuilder.getPrimaryFactoryMethodBuilder();
    coord = getCoordinate();

    // Newspeak-spec: this is not strictly sufficient for Newspeak
    //                it could also parse a binary selector here, I think
    //                but, doesn't seem so useful, so, let's keep it simple
    if (sym == Identifier || sym == Keyword) {
      messagePattern(primaryFactory);
    } else {
      // in the standard case, the primary factory method is #new
      primaryFactory.addArgument("self", getEmptySource());
      primaryFactory.setSignature(Symbols.NEW);
    }
    mxnBuilder.setupInitializerBasedOnPrimaryFactory(getSource(coord));

    expect(Equal, "Unexpected symbol %(found)s."
        + " Tried to parse the class declaration of " + mixinName
        + " and expect '=' before the (optional) inheritance declaration.",
        KeywordTag.class);

    inheritanceListAndOrBody(mxnBuilder);
    return mxnBuilder;
  }

  private void inheritanceListAndOrBody(final MixinBuilder mxnBuilder)
      throws ProgramDefinitionError {
    if (sym == NewTerm) {
      defaultSuperclassAndBody(mxnBuilder);
    } else {
      explicitInheritanceListAndOrBody(mxnBuilder);
    }
  }

  private void defaultSuperclassAndBody(final MixinBuilder mxnBuilder)
      throws ProgramDefinitionError {
    SourceSection source = getEmptySource();
    MethodBuilder def = mxnBuilder.getClassInstantiationMethodBuilder();
    ExpressionNode selfRead = def.getSelfRead(source);
    ExpressionNode superClass = createMessageSend(Symbols.OBJECT,
        new ExpressionNode[] {selfRead}, false, source, null, language);
    mxnBuilder.setSuperClassResolution(superClass);

    mxnBuilder.setSuperclassFactorySend(
        mxnBuilder.createStandardSuperFactorySend(source), true);

    classBody(mxnBuilder);
  }

  private void explicitInheritanceListAndOrBody(final MixinBuilder mxnBuilder)
      throws ProgramDefinitionError {
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
      if (accept(Period, StatementSeparatorTag.class)) {
        // TODO: what else do we need to do here?
        mxnBuilder.setInitializerSource(getSource(initCoord));
        mxnBuilder.finalizeInitializer();
        return;
      }
    }
    classBody(mxnBuilder);
  }

  private void mixinApplication(final MixinBuilder mxnBuilder, final int mixinId)
      throws ProgramDefinitionError {
    expect(MixinOperator, KeywordTag.class);
    SourceCoordinate coord = getCoordinate();

    ExpressionNode mixinResolution = inheritancePrefixAndSuperclass(mxnBuilder);
    mxnBuilder.addMixinResolver(mixinResolution);

    AbstractUninitializedMessageSendNode mixinFactorySend;
    SSymbol uniqueInitName;
    if (sym != NewTerm && sym != MixinOperator && sym != Period) {
      mixinFactorySend = (AbstractUninitializedMessageSendNode) messages(
          mxnBuilder.getInitializerMethodBuilder(),
          mxnBuilder.getInitializerMethodBuilder().getSelfRead(getSource(coord)));

      uniqueInitName = MixinBuilder.getInitializerName(
          mixinFactorySend.getSelector(), mixinId);
      mixinFactorySend = (AbstractUninitializedMessageSendNode) MessageSendNode.adaptSymbol(
          uniqueInitName, mixinFactorySend, language.getVM());
    } else {
      uniqueInitName = MixinBuilder.getInitializerName(Symbols.NEW, mixinId);
      mixinFactorySend = (AbstractUninitializedMessageSendNode)
          createMessageSend(uniqueInitName,
              new ExpressionNode[] {mxnBuilder.getInitializerMethodBuilder().getSelfRead(getSource(coord))},
              false, getSource(coord), null, language);
    }

    mxnBuilder.addMixinFactorySend(mixinFactorySend);
  }

  private void inheritanceClause(final MixinBuilder mxnBuilder)
      throws ProgramDefinitionError {
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
          mxnBuilder.getInitializerMethodBuilder().getSuperReadNode(getEmptySource()));

      SSymbol initializerName = MixinBuilder.getInitializerName(
          ((AbstractUninitializedMessageSendNode) superFactorySend).getSelector());

      // TODO: the false we pass here, should that be conditional on the superFactorSend being a #new send?
      mxnBuilder.setSuperclassFactorySend(
          MessageSendNode.adaptSymbol(
              initializerName,
              (AbstractUninitializedMessageSendNode) superFactorySend,
              language.getVM()), false);
    } else {
      mxnBuilder.setSuperclassFactorySend(
          mxnBuilder.createStandardSuperFactorySend(
              getEmptySource()), true);
    }
  }

  private ExpressionNode inheritancePrefixAndSuperclass(
      final MixinBuilder mxnBuilder) throws ParseError, MixinDefinitionError {
    MethodBuilder meth = mxnBuilder.getClassInstantiationMethodBuilder();
    SourceCoordinate coord = getCoordinate();

    if (acceptIdentifier("outer", KeywordTag.class)) {
      String outer = identifier();
      ExpressionNode self = meth.getOuterRead(outer, getSource(coord));
      if (sym == Identifier) {
        return unaryMessage(self, false, null);
      } else {
        return self;
      }
    }

    ExpressionNode self;
    if (acceptIdentifier("super", KeywordTag.class)) {
      self = meth.getSuperReadNode(getSource(coord));
    } else if (acceptIdentifier("self", KeywordTag.class)) {
      self = meth.getSelfRead(getSource(coord));
    } else {
      return meth.getImplicitReceiverSend(unarySelector(), getSource(coord));
    }
    return unaryMessage(self, false, null);
  }

  private void classBody(final MixinBuilder mxnBuilder) throws ProgramDefinitionError {
    classHeader(mxnBuilder);
    sideDeclaration(mxnBuilder);
    if (sym == Colon) {
      classSideDecl(mxnBuilder);
    }
  }

  private void classSideDecl(final MixinBuilder mxnBuilder)
      throws ProgramDefinitionError {
    mxnBuilder.switchToClassSide();

    expect(Colon, KeywordTag.class);
    expect(NewTerm, null);

    while (sym != EndTerm) {
      comments();
      methodDeclaration(mxnBuilder);
      comments();
    }

    expect(EndTerm, null);
  }

  private void classHeader(final MixinBuilder mxnBuilder)
      throws ProgramDefinitionError {
    expect(NewTerm, null);
    classComment(mxnBuilder);

    SourceCoordinate coord = getCoordinate();
    if (sym == Or) {
      slotDeclarations(mxnBuilder);
    }

    comments();
    if (sym != EndTerm) {
      initExprs(mxnBuilder);
    }

    mxnBuilder.setInitializerSource(getSource(coord));
    expect(EndTerm, null);
    mxnBuilder.finalizeInitializer();
  }

  private void classComment(final MixinBuilder mxnBuilder) throws ParseError {
    mxnBuilder.setComment(comments());
  }

  private String comments() throws ParseError {
    String comment = "";
    while (sym == BeginComment) {
      if (comment.length() > 0) {
        comment += "\n";
      }
      comment += comment();
    }

    return comment;
  }

  private String comment() throws ParseError {
    SourceCoordinate coord = getCoordinate();

    expect(BeginComment, null);

    String comment = "";
    while (sym != EndComment) {
      comment += lexer.getCommentPart();
      getSymbolFromLexer();

      if (sym == BeginComment) {
        comment += "(*" + comments() + "*)";
      }
      if (sym == NONE) {
        throw new ParseError("Comment seems not to be closed", EndComment, this);
      }
    }
    expect(EndComment, null);
    language.getVM().reportSyntaxElement(CommentTag.class, getSource(coord));
    return comment;
  }

  private void slotDeclarations(final MixinBuilder mxnBuilder)
      throws ProgramDefinitionError {
    // Newspeak-speak: we do not support simSlotDecls, i.e.,
    //                 simultaneous slots clauses (spec 6.3.2)
    expect(Or, DelimiterOpeningTag.class);

    while (sym != Or) {
      slotDefinition(mxnBuilder);
    }

    comments();

    expect(Or, DelimiterClosingTag.class);
  }

  private void slotDefinition(final MixinBuilder mxnBuilder)
      throws ProgramDefinitionError {
    comments();
    if (sym == Or) { return; }

    SourceCoordinate coord = getCoordinate();
    AccessModifier acccessModifier = accessModifier();

    String slotName = slotDecl();
    boolean immutable;
    ExpressionNode init;

    if (accept(Equal, KeywordTag.class)) {
      immutable = true;
      init = expression(mxnBuilder.getInitializerMethodBuilder());
      expect(Period, StatementSeparatorTag.class);
    } else if (accept(SlotMutableAssign, KeywordTag.class)) {
      immutable = false;
      init = expression(mxnBuilder.getInitializerMethodBuilder());
      expect(Period, StatementSeparatorTag.class);
    } else {
      immutable = false;
      init = null;
    }
    mxnBuilder.addSlot(symbolFor(slotName), acccessModifier, immutable, init,
        getSource(coord));
  }

  private AccessModifier accessModifier() {
    if (sym == Identifier) {
      if (acceptIdentifier("private",   KeywordTag.class)) { return AccessModifier.PRIVATE;   }
      if (acceptIdentifier("protected", KeywordTag.class)) { return AccessModifier.PROTECTED; }
      if (acceptIdentifier("public",    KeywordTag.class)) { return AccessModifier.PUBLIC;    }
    }
    return AccessModifier.PROTECTED;
  }

  private String slotDecl() throws ParseError {
    return identifier();
  }

  private void initExprs(final MixinBuilder mxnBuilder) throws ProgramDefinitionError {
    MethodBuilder initializer = mxnBuilder.getInitializerMethodBuilder();
    mxnBuilder.addInitializerExpression(expression(initializer));

    while (accept(Period, StatementSeparatorTag.class)) {
      if (sym != EndTerm) {
        mxnBuilder.addInitializerExpression(expression(initializer));
      }
    }
  }

  private void sideDeclaration(final MixinBuilder mxnBuilder) throws ProgramDefinitionError {
    expect(NewTerm, DelimiterOpeningTag.class);
    comments();

    while (canAcceptIdentifierWithOptionalEarlierIdentifier(
        new String[]{"private", "protected", "public"}, "class")) {
      nestedClassDeclaration(mxnBuilder);
      comments();
    }

    while (sym != EndTerm) {
      comments();
      methodDeclaration(mxnBuilder);
      comments();
    }

    expect(EndTerm, DelimiterClosingTag.class);
  }

  private void nestedClassDeclaration(final MixinBuilder mxnBuilder) throws ProgramDefinitionError {
    SourceCoordinate coord = getCoordinate();
    AccessModifier accessModifier = accessModifier();
    MixinBuilder nestedCls = classDeclaration(mxnBuilder, accessModifier);
    mxnBuilder.addNestedMixin(nestedCls.assemble(getSource(coord)));
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

  private boolean acceptIdentifier(final String identifier,
      final Class<? extends Tags> tag) {
    if (sym == Identifier && identifier.equals(text)) {
      accept(Identifier, tag);
      return true;
    }
    return false;
  }

  private boolean accept(final Symbol s, final Class<? extends Tags> tag) {
    if (sym == s) {
      SourceCoordinate coord = tag == null ? null : getCoordinate();
      getSymbolFromLexer();
      if (tag != null) {
        language.getVM().reportSyntaxElement(tag, getSource(coord));
      }
      return true;
    }
    return false;
  }

  private boolean acceptOneOf(final Symbol[] ss, final Class<? extends Tags> tag) {
    if (symIn(ss)) {
      SourceCoordinate coord = tag == null ? null : getCoordinate();
      getSymbolFromLexer();
      if (tag != null) {
        language.getVM().reportSyntaxElement(tag, getSource(coord));
      }
      return true;
    }
    return false;
  }

  private void expectIdentifier(final String identifier, final String msg,
      final Class<? extends Tags> tag) throws ParseError {
    if (acceptIdentifier(identifier, tag)) { return; }

    throw new ParseError(msg, Identifier, this);
  }

  private void expectIdentifier(final String identifier,
      final Class<? extends Tags> tag) throws ParseError {
    expectIdentifier(identifier, "Unexpected token. Expected '" + identifier +
        "', but found %(found)s", tag);
  }

  private void expect(final Symbol s, final String msg,
      final Class<? extends Tags> tag) throws ParseError {
    if (accept(s, tag)) { return; }

    throw new ParseError(msg, s, this);
  }

  private void expect(final Symbol s, final Class<? extends Tags> tag) throws ParseError {
    expect(s, "Unexpected symbol. Expected %(expected)s, but found %(found)s", tag);
  }

  private boolean expectOneOf(final Symbol[] ss, final Class<? extends Tags> tag) throws ParseError {
    if (acceptOneOf(ss, tag)) { return true; }

    throw new ParseErrorWithSymbols("Unexpected symbol. Expected one of " +
        "%(expected)s, but found %(found)s", ss, this);
  }

  SourceSection getEmptySource() {
    SourceCoordinate coord = getCoordinate();
    return source.createSection(coord.charIndex, 0);
  }

  public SourceSection getSource(final SourceCoordinate coord) {
    assert lexer.getNumberOfCharactersRead() - coord.charIndex >= 0;
    SourceSection ss = source.createSection(coord.charIndex,
        Math.max(lexer.getNumberOfNonWhiteCharsRead() - coord.charIndex, 0));
    return ss;
  }

  private void methodDeclaration(final MixinBuilder mxnBuilder)
      throws ProgramDefinitionError {
    SourceCoordinate coord = getCoordinate();

    AccessModifier accessModifier = accessModifier();
    MethodBuilder builder = new MethodBuilder(
        mxnBuilder, mxnBuilder.getScopeForCurrentParserPosition());

    messagePattern(builder);
    expect(Equal,
        "Unexpected symbol %(found)s. Tried to parse method declaration and expect '=' between message pattern, and method body.",
        KeywordTag.class);
    ExpressionNode body = methodBlock(builder);
    builder.finalizeMethodScope();
    SInvokable meth = builder.assemble(body, accessModifier, getSource(coord));

    if (structuralProbe != null) {
      structuralProbe.recordNewMethod(meth);
    }
    mxnBuilder.addMethod(meth);
  }

  private void messagePattern(final MethodBuilder builder) throws ParseError {
    builder.addArgument("self", getEmptySource());
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

  protected void unaryPattern(final MethodBuilder builder) throws ParseError {
    SourceCoordinate coord = getCoordinate();
    builder.setSignature(unarySelector());
    builder.addMethodDefinitionSource(getSource(coord));
  }

  protected void binaryPattern(final MethodBuilder builder) throws ParseError {
    SourceCoordinate coord = getCoordinate();
    builder.setSignature(binarySelector());
    builder.addMethodDefinitionSource(getSource(coord));

    coord = getCoordinate();
    builder.addArgument(argument(), getSource(coord));
  }

  protected void keywordPattern(final MethodBuilder builder) throws ParseError {
    StringBuilder kw = new StringBuilder();
    do {
      SourceCoordinate coord = getCoordinate();
      kw.append(keyword());
      builder.addMethodDefinitionSource(getSource(coord));

      coord = getCoordinate();
      builder.addArgument(argument(), getSource(coord));
    }
    while (sym == Keyword);

    builder.setSignature(symbolFor(kw.toString()));
  }

  private ExpressionNode methodBlock(final MethodBuilder builder)
      throws ProgramDefinitionError {
    SourceCoordinate coord = getCoordinate();
    expect(NewTerm, DelimiterOpeningTag.class);

    ExpressionNode methodBody = blockContents(builder);
    expect(EndTerm, DelimiterClosingTag.class);
    lastMethodsSourceSection = getSource(coord);
    return methodBody;
  }

  protected SSymbol unarySelector() throws ParseError {
    return symbolFor(identifier());
  }

  protected SSymbol binarySelector() throws ParseError {
    String s = text;

    // Checkstyle: stop
    if (accept(Or, null)) {
    } else if (accept(Comma, null)) {
    } else if (accept(Minus, null)) {
    } else if (accept(Equal, null)) {
    } else if (acceptOneOf(singleOpSyms, null)) {
    } else if (accept(OperatorSequence, null)) {
    } else {
      throw new ParseError("Unexpected symbol. Expected binary operator, "
          + "but found %(found)s", Symbol.NONE, this);
    }
    // Checkstyle: resume

    return symbolFor(s);
  }

  private String identifier() throws ParseError {
    String s = text;
    expect(Identifier, null);
    return s;
  }

  protected String keyword() throws ParseError {
    String s = text;
    expect(Keyword, null);

    return s;
  }

  private String argument() throws ParseError {
    SourceCoordinate coord = getCoordinate();
    String id = identifier();
    language.getVM().reportSyntaxElement(ArgumentTag.class, getSource(coord));
    return id;
  }

  private ExpressionNode blockContents(final MethodBuilder builder)
      throws ProgramDefinitionError {
    comments();
    if (accept(Or, DelimiterOpeningTag.class)) {
      locals(builder);
      expect(Or, DelimiterClosingTag.class);
    }
    builder.setVarsOnMethodScope();
    return blockBody(builder);
  }

  private void locals(final MethodBuilder builder) throws ParseError, MethodDefinitionError {
    while (sym == Identifier) {
      SourceCoordinate coord = getCoordinate();
      String id = identifier();
      SourceSection source = getSource(coord);
      builder.addLocal(id, source);
      language.getVM().reportSyntaxElement(LocalVariableTag.class, source);
    }
  }

  private ExpressionNode blockBody(final MethodBuilder builder) throws ProgramDefinitionError {
    SourceCoordinate coord = getCoordinate();
    List<ExpressionNode> expressions = new ArrayList<ExpressionNode>();

    boolean sawPeriod = true;

    while (true) {
      comments();

      if (accept(Exit, KeywordTag.class)) {
        if (!sawPeriod) {
          expect(Period, null);
        }
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

      if (!sawPeriod) {
        expect(Period, null);
      }

      expressions.add(expression(builder));
      sawPeriod = accept(Period, StatementSeparatorTag.class);
    }
  }

  private ExpressionNode result(final MethodBuilder builder)
      throws ProgramDefinitionError {
    SourceCoordinate coord = getCoordinate();

    ExpressionNode exp = expression(builder);
    accept(Period, StatementSeparatorTag.class);

    if (builder.isBlockMethod()) {
      return builder.getNonLocalReturn(exp, getSource(coord));
    } else {
      return exp;
    }
  }

  private ExpressionNode expression(final MethodBuilder builder)
      throws ProgramDefinitionError {
    comments();
    peekForNextSymbolFromLexer();

    if (nextSym == Assign) {
      return assignation(builder);
    } else {
      return evaluation(builder);
    }
  }

  private ExpressionNode assignation(final MethodBuilder builder)
      throws ProgramDefinitionError {
    return assignments(builder);
  }

  protected ExpressionNode assignments(final MethodBuilder builder)
      throws ProgramDefinitionError {
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

  protected SSymbol assignment() throws ParseError {
    SSymbol id = symbolFor(identifier());
    expect(Assign, KeywordTag.class);
    return id;
  }

  private ExpressionNode evaluation(final MethodBuilder builder)
      throws ProgramDefinitionError {
    ExpressionNode exp;
    if (sym == Keyword) {
      exp = keywordMessage(builder, builder.getSelfRead(getEmptySource()), false, false, null);
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

  private ExpressionNode primary(final MethodBuilder builder) throws ProgramDefinitionError {
    switch (sym) {
      case Identifier: {
        SourceCoordinate coord = getCoordinate();
        // Parse true, false, and nil as keyword-like constructs
        // (cf. Newspeak spec on reserved words)
        if (acceptIdentifier("true", LiteralTag.class)) {
          return new TrueLiteralNode(getSource(coord));
        }
        if (acceptIdentifier("false", LiteralTag.class)) {
          return new FalseLiteralNode(getSource(coord));
        }
        if (acceptIdentifier("nil", LiteralTag.class)) {
          return new NilLiteralNode(getSource(coord));
        }
        if ("outer".equals(text)) {
          return outerSend(builder);
        }

        SSymbol selector = unarySelector();
        return builder.getImplicitReceiverSend(selector, getSource(coord));
      }
      case NewTerm: {
        return nestedTerm(builder);
      }
      case NewBlock: {
        MethodBuilder bgenc = new MethodBuilder(builder);

        ExpressionNode blockBody = nestedBlock(bgenc);
        bgenc.finalizeMethodScope();

        SInvokable blockMethod = bgenc.assemble(blockBody,
            AccessModifier.BLOCK_METHOD, lastMethodsSourceSection);
        builder.addEmbeddedBlockMethod(blockMethod);

        if (bgenc.requiresContext()) {
          return new BlockNodeWithContext(blockMethod, lastMethodsSourceSection);
        } else {
          return new BlockNode(blockMethod, lastMethodsSourceSection);
        }
      }
      case NewLiteralArray: {
        return literalArray(builder);
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
      throws ProgramDefinitionError {
    SourceCoordinate coord = getCoordinate();
    expectIdentifier("outer", KeywordTag.class);
    String outer = identifier();

    ExpressionNode operand = builder.getOuterRead(outer, getSource(coord));
    operand = binaryConsecutiveMessages(builder, operand, false, null);
    return operand;
  }

  protected ExpressionNode binaryConsecutiveMessages(
      final MethodBuilder builder, ExpressionNode operand,
      boolean eventualSend, SourceSection sendOp) throws ProgramDefinitionError {
    while (sym == OperatorSequence || symIn(binaryOpSyms)) {
      operand = binaryMessage(builder, operand, eventualSend, sendOp);
      SourceCoordinate coord = getCoordinate();

      eventualSend = accept(EventualSend, KeywordTag.class);
      if (eventualSend) {
        sendOp = getSource(coord);
      }
    }
    return operand;
  }

  private ExpressionNode messages(final MethodBuilder builder,
      final ExpressionNode receiver) throws ProgramDefinitionError {
    ExpressionNode msg;
    SourceCoordinate coord = getCoordinate();
    boolean eventualSend = accept(EventualSend, KeywordTag.class);

    SourceSection sendOp = null;
    if (eventualSend) {
      sendOp = getSource(coord);
    }

    if (sym == Identifier) {
      msg = unaryMessage(receiver, eventualSend, sendOp);
      eventualSend = accept(EventualSend, KeywordTag.class);
      if (eventualSend) {
        sendOp = getSource(coord);
      }

      while (sym == Identifier) {
        msg = unaryMessage(msg, eventualSend, sendOp);
        eventualSend = accept(EventualSend, KeywordTag.class);
        if (eventualSend) {
          sendOp = getSource(coord);
        }
      }

      if (sym == OperatorSequence || symIn(binaryOpSyms)) {
        msg = binaryConsecutiveMessages(builder, msg, eventualSend, sendOp);
        eventualSend = accept(EventualSend, KeywordTag.class);
        if (eventualSend) {
          sendOp = getSource(coord);
        }
      }

      if (sym == Keyword) {
        msg = keywordMessage(builder, msg, true, eventualSend, sendOp);
      }
    } else if (sym == OperatorSequence || symIn(binaryOpSyms)) {
      msg = binaryConsecutiveMessages(builder, receiver, eventualSend, sendOp);
      eventualSend = accept(EventualSend, KeywordTag.class);
      if (eventualSend) {
        sendOp = getSource(coord);
      }

      if (sym == Keyword) {
        msg = keywordMessage(builder, msg, true, eventualSend, sendOp);
      }
    } else {
      msg = keywordMessage(builder, receiver, true, eventualSend, sendOp);
    }
    return msg;
  }

  protected ExpressionNode unaryMessage(final ExpressionNode receiver,
      final boolean eventualSend, final SourceSection sendOperator) throws ParseError {
    SourceCoordinate coord = getCoordinate();
    SSymbol selector = unarySelector();
    return createMessageSend(selector, new ExpressionNode[] {receiver},
        eventualSend, getSource(coord), sendOperator, language);
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

  protected ExpressionNode binaryMessage(final MethodBuilder builder,
      final ExpressionNode receiver, final boolean eventualSend,
      final SourceSection sendOperator) throws ProgramDefinitionError {
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
        eventualSend, getSource(coord), sendOperator, language);
  }

  private ExpressionNode binaryOperand(final MethodBuilder builder) throws ProgramDefinitionError {
    ExpressionNode operand = primary(builder);

    // a binary operand can receive unaryMessages
    // Example: 2 * 3 asString
    //   is evaluated as 2 * (3 asString)
    SourceCoordinate coord = getCoordinate();
    boolean evenutalSend = accept(EventualSend, KeywordTag.class);
    while (sym == Identifier) {
      SourceSection sendOp = null;
      if (evenutalSend) {
        sendOp = getSource(coord);
      }
      operand = unaryMessage(operand, evenutalSend, sendOp);
      evenutalSend = accept(EventualSend, KeywordTag.class);
    }

    assert !evenutalSend : "eventualSend should not be true, because that means we steal it from the next operation (think here shouldn't be one, but still...)";
    return operand;
  }

  // TODO: if the eventual send is not consumed by an expression (assignment, etc)
  //       we don't need to create a promise

  protected ExpressionNode keywordMessage(final MethodBuilder builder,
      final ExpressionNode receiver, final boolean explicitRcvr,
      final boolean eventualSend, final SourceSection sendOperator) throws ProgramDefinitionError {
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

    SourceSection source = getSource(coord);
    ExpressionNode[] args = arguments.toArray(new ExpressionNode[0]);
    if (explicitRcvr) {
      return createMessageSend(
          msg, args, eventualSend, source, sendOperator, language);
    } else {
      assert !eventualSend;
      return createImplicitReceiverSend(msg, args,
          builder.getCurrentMethodScope(),
          builder.getEnclosingMixinBuilder().getMixinId(), source, language.getVM());
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
          condition.markAsControlFlowCondition();
          ExpressionNode inlinedBody = ((LiteralNode) arguments.get(1)).inline(builder);
          return new IfInlinedLiteralNode(condition, true, inlinedBody,
              arguments.get(1), source);
        } else if ("ifFalse:".equals(msgStr)) {
          ExpressionNode condition = arguments.get(0);
          condition.markAsControlFlowCondition();
          ExpressionNode inlinedBody = ((LiteralNode) arguments.get(1)).inline(builder);
          return new IfInlinedLiteralNode(condition, false, inlinedBody,
              arguments.get(1), source);
        } else if ("whileTrue:".equals(msgStr)) {
          ExpressionNode inlinedCondition = ((LiteralNode) arguments.get(0)).inline(builder);
          inlinedCondition.markAsControlFlowCondition();
          ExpressionNode inlinedBody      = ((LiteralNode) arguments.get(1)).inline(builder);
          inlinedBody.markAsLoopBody();
          return new WhileInlinedLiteralsNode(inlinedCondition, inlinedBody,
              true, arguments.get(0), arguments.get(1), source);
        } else if ("whileFalse:".equals(msgStr)) {
          ExpressionNode inlinedCondition = ((LiteralNode) arguments.get(0)).inline(builder);
          inlinedCondition.markAsControlFlowCondition();
          ExpressionNode inlinedBody      = ((LiteralNode) arguments.get(1)).inline(builder);
          inlinedBody.markAsLoopBody();
          return new WhileInlinedLiteralsNode(inlinedCondition, inlinedBody,
              false, arguments.get(0), arguments.get(1), source);
        } else if ("or:".equals(msgStr) || "||".equals(msgStr)) {
          ExpressionNode inlinedArg = ((LiteralNode) arguments.get(1)).inline(builder);
          return new OrInlinedLiteralNode(arguments.get(0), inlinedArg, arguments.get(1), source);
        } else if ("and:".equals(msgStr) || "&&".equals(msgStr)) {
          ExpressionNode inlinedArg = ((LiteralNode) arguments.get(1)).inline(builder);
          return new AndInlinedLiteralNode(arguments.get(0), inlinedArg, arguments.get(1), source);
        } else if (!VmSettings.DYNAMIC_METRICS && "timesRepeat:".equals(msgStr)) {
          ExpressionNode inlinedBody = ((LiteralNode) arguments.get(1)).inline(builder);
          inlinedBody.markAsLoopBody();
          return IntTimesRepeatLiteralNodeGen.create(inlinedBody,
              arguments.get(1), source, arguments.get(0));
        }
      }
    } else if (numberOfArguments == 3) {
      if ("ifTrue:ifFalse:".equals(msgStr) &&
          arguments.get(1) instanceof LiteralNode && arguments.get(2) instanceof LiteralNode) {
        LiteralNode blockOrVal = (LiteralNode) arguments.get(2);
        ExpressionNode condition = arguments.get(0);
        condition.markAsControlFlowCondition();
        ExpressionNode inlinedTrueNode  = ((LiteralNode) arguments.get(1)).inline(builder);
        ExpressionNode inlinedFalseNode = blockOrVal.inline(builder);
        return new IfTrueIfFalseInlinedLiteralsNode(condition,
            inlinedTrueNode, inlinedFalseNode, arguments.get(1), arguments.get(2),
            source);
      } else if (!VmSettings.DYNAMIC_METRICS && "to:do:".equals(msgStr) &&
          arguments.get(2) instanceof LiteralNode) {
        LiteralNode blockOrVal = (LiteralNode) arguments.get(2);
        try {
          ExpressionNode inlinedBody = blockOrVal.inline(builder);
          inlinedBody.markAsLoopBody();

          Local loopIdx = getLoopIdx(builder, blockOrVal, source);
          return IntToDoInlinedLiteralsNodeGen.create(inlinedBody, loopIdx,
              arguments.get(2), source, arguments.get(0), arguments.get(1));
        } catch (MethodDefinitionError e) {
          throw new RuntimeException(e);
        }
      } else if (!VmSettings.DYNAMIC_METRICS && "downTo:do:".equals(msgStr) &&
          arguments.get(2) instanceof LiteralNode) {
        LiteralNode blockOrVal = (LiteralNode) arguments.get(2);
        try {
          ExpressionNode inlinedBody = blockOrVal.inline(builder);
          inlinedBody.markAsLoopBody();

          Local loopIdx = getLoopIdx(builder, blockOrVal, source);
          return IntDownToDoInlinedLiteralsNodeGen.create(inlinedBody, loopIdx,
              arguments.get(2), source, arguments.get(0), arguments.get(1));
        } catch (MethodDefinitionError e) {
          throw new RuntimeException(e);
        }
      }
    }
    return null;
  }

  private Local getLoopIdx(final MethodBuilder builder,
      final LiteralNode blockOrVal, final SourceSection source)
      throws MethodDefinitionError {
    Local loopIdx;
    if (blockOrVal instanceof BlockNode) {
      Argument[] args = ((BlockNode) blockOrVal).getArguments();
      assert args.length == 2;
      loopIdx = builder.getLocal(args[1].getQualifiedName());
    } else {
      // if it is a literal, we still need a memory location for counting, so,
      // add a synthetic local
      loopIdx = builder.addLocalAndUpdateScope(
          "!i" + SourceCoordinate.getLocationQualifier(source), source);
    }
    return loopIdx;
  }

  private ExpressionNode formula(final MethodBuilder builder)
      throws ProgramDefinitionError {
    ExpressionNode operand = binaryOperand(builder);
    SourceCoordinate coord = getCoordinate();
    boolean evenutalSend = accept(EventualSend, KeywordTag.class);
    SourceSection sendOp = null;
    if (evenutalSend) {
      sendOp = getSource(coord);
    }

    operand = binaryConsecutiveMessages(builder, operand, evenutalSend, sendOp);
    return operand;
  }

  private ExpressionNode nestedTerm(final MethodBuilder builder)
      throws ProgramDefinitionError {
    expect(NewTerm, DelimiterOpeningTag.class);
    ExpressionNode exp = expression(builder);
    expect(EndTerm, DelimiterClosingTag.class);
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
    expect(Minus, null);
    return literalDecimal(true, coord);
  }

  private LiteralNode literalInteger(final boolean isNegative,
      final SourceCoordinate coord) throws ParseError {
    try {
       long i = Long.parseLong(text);
       if (isNegative) {
         i = 0 - i;
       }
       expect(Integer, null);

       SourceSection source = getSource(coord);
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
      expect(Double, null);
      double d = java.lang.Double.parseDouble(doubleText);
      if (isNegative) {
        d = 0.0 - d;
      }
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
    expect(Pound, null);
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

  private LiteralNode literalArray(final MethodBuilder builder) throws ProgramDefinitionError {
    SourceCoordinate coord = getCoordinate();
    List<ExpressionNode> expressions = new ArrayList<ExpressionNode>();

    expect(NewLiteralArray, DelimiterOpeningTag.class);

    boolean needsSeparator = false;
    while (true) {
      comments();
      if (sym == EndLiteralArray) {
        expect(EndLiteralArray, DelimiterClosingTag.class);
        return createLiteralArray(expressions, getSource(coord));
      }
      if (needsSeparator) {
        expect(Period,
            "Could not parse statement. Expected a '.' but got '" + text + "'",
            StatementSeparatorTag.class);
      }
      expressions.add(expression(builder));
      needsSeparator = !accept(Period, StatementSeparatorTag.class);
    }
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
    String s = text;
    expectOneOf(keywordSelectorSyms, null);
    SSymbol symb = symbolFor(s);
    return symb;
  }

  private String string() throws ParseError {
    String s = text;
    expect(STString, null);
    return s;
  }

  private String stripColons(final String str) {
    return str.replace(":", "");
  }

  private ExpressionNode nestedBlock(final MethodBuilder builder) throws ProgramDefinitionError {
    SourceCoordinate coord = getCoordinate();
    expect(NewBlock, DelimiterOpeningTag.class);


    builder.addArgument("$blockSelf", getEmptySource());

    if (sym == Colon) {
      blockPattern(builder);
    }

    String outerMethodName = stripColons(builder.getOuterBuilder().
        getSignature().getString());

    // generate Block signature
    String blockSig = "λ" + outerMethodName + "@" + lexer.getCurrentLineNumber() + "@" + lexer.getCurrentColumn();
    int argSize = builder.getNumberOfArguments();
    for (int i = 1; i < argSize; i++) {
      blockSig += ":";
    }

    builder.setSignature(symbolFor(blockSig));

    ExpressionNode expressions = blockContents(builder);

    expect(EndBlock, DelimiterClosingTag.class);
    lastMethodsSourceSection = getSource(coord);

    return expressions;
  }

  private void blockPattern(final MethodBuilder builder) throws ParseError {
    blockArguments(builder);
    expect(Or, KeywordTag.class);
  }

  private void blockArguments(final MethodBuilder builder) throws ParseError {
    do {
      expect(Colon, KeywordTag.class);
      SourceCoordinate coord = getCoordinate();
      builder.addArgument(argument(), getSource(coord));
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
