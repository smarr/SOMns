package som.compiler;

import static org.junit.Assert.assertEquals;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.source.Source;

import som.interpreter.SomLanguage;


public class LexerTest {
  private Source s;

  private Lexer init(final String code) {
    s = SomLanguage.getSyntheticSource(code, "test");
    return new Lexer(code);
  }

  @Test
  public void testStartCoordinate() {
    Lexer l = init("Foo = ()");

    int startIndex = l.getNumberOfCharactersRead();
    assertEquals(0, startIndex);
    assertEquals(1, s.getLineNumber(startIndex));
    assertEquals(1, s.getColumnNumber(startIndex));
  }

  @Test
  public void testFirstToken() {
    Lexer l = init("Foo = ()");
    l.getSym();

    int startIndex = l.getNumberOfCharactersRead();
    assertEquals(0, startIndex);
    assertEquals(1, s.getLineNumber(startIndex));
    assertEquals(1, s.getColumnNumber(startIndex));
  }

  @Test
  public void testSecondToken() {
    Lexer l = init("Foo = ()");
    l.getSym();
    l.getSym();

    int startIndex = l.getNumberOfCharactersRead();
    assertEquals(4, startIndex);
    assertEquals(1, s.getLineNumber(startIndex));
    assertEquals(5, s.getColumnNumber(startIndex));
  }

  @Test
  public void testFirstTokenAfterSpace() {
    Lexer l = init(" Foo = ()");
    l.getSym();

    int startIndex = l.getNumberOfCharactersRead();
    assertEquals(1, startIndex);
    assertEquals(1, s.getLineNumber(startIndex));
    assertEquals(2, s.getColumnNumber(startIndex));
  }

  @Test
  public void testSecondLineFirstToken() {
    Lexer l = init("\nFoo = ()");
    Symbol sym = l.getSym();

    assertEquals(Symbol.Identifier, sym);
    assertEquals("Foo", l.getText());

    int startIndex = l.getNumberOfCharactersRead();
    assertEquals(1, startIndex);
    assertEquals(2, s.getLineNumber(startIndex));
    assertEquals(1, s.getColumnNumber(startIndex));
  }

  @Test
  public void testSecondLineSecondToken() {
    Lexer l = init("\nFoo = ()");
    l.getSym();
    Symbol sym = l.getSym();

    assertEquals(Symbol.Equal, sym);
    assertEquals("=", l.getText());

    int startIndex = l.getNumberOfCharactersRead();
    assertEquals(5, startIndex);
    assertEquals(2, s.getLineNumber(startIndex));
    assertEquals(5, s.getColumnNumber(startIndex));
  }

  @Test
  public void testSecondLineMethodToken() {
    String prefix = "Foo = (\n" + "  ";
    Lexer l = init("Foo = (\n"
        + "  method = ( ) )");
    l.getSym();
    l.getSym();
    l.getSym();
    Symbol sym = l.getSym();

    assertEquals(Symbol.Identifier, sym);
    assertEquals("method", l.getText());

    int startIndex = l.getNumberOfCharactersRead();
    assertEquals(2, s.getLineNumber(startIndex));
    assertEquals(3, s.getColumnNumber(startIndex));

    assertEquals(prefix.length(), startIndex);
  }

  @Test
  public void testSecondLineMethodNoSpacesToken() {
    String prefix = "Foo = (\n" + "";
    Lexer l = init("Foo = (\n"
        + "method = ( ) )");
    l.getSym();
    l.getSym();
    l.getSym();
    Symbol sym = l.getSym();

    assertEquals(Symbol.Identifier, sym);
    assertEquals("method", l.getText());

    int startIndex = l.getNumberOfCharactersRead();
    assertEquals(2, s.getLineNumber(startIndex));
    assertEquals(1, s.getColumnNumber(startIndex));

    assertEquals(prefix.length(), startIndex);
  }

  @Test
  public void testSourceCordinates() {
    Lexer l = init("class Hello usingPlatform: platform = Value ()(\n" +
        "  public main: args = (\n" +
        "    'Hello World!' println.\n" +
        "    args from: 2 to: args size do: [ :arg | arg print. ' ' print ].\n" +
        "    '' println.\n" +
        "    ^ 0\n" +
        "  )\n" +
        ")\n" +
        "");
    // start line 1 col 1
    l.getSym();
    int startIndex = l.getNumberOfCharactersRead();
    Assert.assertEquals(1, s.getLineNumber(startIndex));
    Assert.assertEquals(1, s.getColumnNumber(startIndex));

    // second token
    l.getSym();
    startIndex = l.getNumberOfCharactersRead();
    Assert.assertEquals(1, s.getLineNumber(startIndex));
    Assert.assertEquals(7, s.getColumnNumber(startIndex));

    for (int i = 0; i < 7; i++) {
      l.getSym();
      startIndex = l.getNumberOfCharactersRead();
      Assert.assertEquals(1, s.getLineNumber(startIndex));
    }

    // line 2
    l.getSym();
    startIndex = l.getNumberOfCharactersRead();
    Assert.assertEquals(2, s.getLineNumber(startIndex));
    Assert.assertEquals(3, s.getColumnNumber(startIndex));
  }
}
