package som.compiler;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.source.Source;

import bd.tools.structure.StructuralProbe;
import som.compiler.Parser.ParseError;
import som.interpreter.SomLanguage;


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
        "<{String. Foobar}>"};
  }

  @Test
  public void testNormalType() throws RuntimeException, ParseError {
    // add a space so that lexer stops lexing
    String testString = content + " ";
    TypeParser tp = createParser(testString);
    tp.parseType();
    assertTrue("Should reach here, and parse without error", true);
  }

  @Test
  public void testReturnType() throws RuntimeException, ParseError {
    // add a space so that lexer stops lexing
    String testString = "^ " + content + " ";
    TypeParser tp = createParser(testString);
    tp.parseReturnType();
    assertTrue("Should reach here, and parse without error", true);
  }

  private TypeParser createParser(final String testString) throws ParseError {
    Source s = SomLanguage.getSyntheticSource(testString, "test.ns");
    Parser p = new Parser(testString, s, new StructuralProbe<>(), new SomLanguage());

    TypeParser tp = new TypeParser(p);
    return tp;
  }
}
