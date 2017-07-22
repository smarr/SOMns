package som.compiler;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.source.MissingMIMETypeException;
import com.oracle.truffle.api.source.MissingNameException;
import com.oracle.truffle.api.source.Source;

import som.compiler.Parser.ParseError;
import som.interpreter.SomLanguage;
import tools.language.StructuralProbe;


@RunWith(Parameterized.class)
public class TypeGrammarParserTest {

  private final String content;

  public TypeGrammarParserTest(final String content) {
    this.content = content;
  }

  @Parameters(name = "{0}")
  public static String[] data() {
    return new String[] {
        "<MyType>", "<MyType>", "<List[FooBar]>", "<[:FooBar]>",
        "<[:String :ObjectMirror]>", "<List[Promise[V, E]]>",
        "<WeakMap[FarReference, InternalFarReference]>",
        "<[:V | V2 def]>", "<[:Promise | R def] | [R def]>",
        "<{String, Foobar}>"};
  }

  @Test
  public void testNormalType() throws RuntimeException,
      MissingMIMETypeException, MissingNameException, ParseError {
    // add a space so that lexer stops lexing
    String testString = content + " ";
    TypeParser tp = createParser(testString);
    tp.parseType();
  }

  @Test
  public void testReturnType() throws RuntimeException,
      MissingMIMETypeException, MissingNameException, ParseError {
    // add a space so that lexer stops lexing
    String testString = "^ " + content + " ";
    TypeParser tp = createParser(testString);
    tp.parseReturnType();
  }

  private TypeParser createParser(final String testString)
      throws MissingMIMETypeException, MissingNameException, ParseError {
    Source s = Source.newBuilder(testString).
        name("test.som").
        mimeType(SomLanguage.MIME_TYPE).build();
    Parser p = new Parser(
        testString, testString.length(), s, new StructuralProbe(),
        new SomLanguage());

    TypeParser tp = new TypeParser(p);
    return tp;
  }

}
