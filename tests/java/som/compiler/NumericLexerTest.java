package som.compiler;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;


@RunWith(Enclosed.class)
public class NumericLexerTest {

  @RunWith(Parameterized.class)
  public static class ValidNumerals {
    @Parameters(name = "{0}")
    public static Iterable<? extends Object> data() {
      String[] signs = new String[] {"", "-"};
      String[] digits = new String[] {"1", /* "12312", "33", "1234567890", "0", */ "99"};

      String[] signedDigits = new String[signs.length * digits.length];
      int i = 0;
      for (String s : signs) {
        for (String d : digits) {
          signedDigits[i] = s + d;
          i += 1;
        }
      }

      String[] decimalNums = new String[signedDigits.length * digits.length * signedDigits.length +
                                        signedDigits.length * digits.length +
                                        signedDigits.length * signedDigits.length +
                                        signedDigits.length];
      i = 0;

      // (char: ”-”) opt, digits, fraction opt, exponent opt.
      for (String d : signedDigits) {
        for (String f : digits) {
          for (String e : signedDigits) {
            decimalNums[i] = d + "." + f + "e" + e;
            i += 1;
          }
        }
      }

      // (char: ”-”) opt, digits, fraction opt
      for (String d : signedDigits) {
        for (String f : digits) {
          decimalNums[i] = d + "." + f;
          i += 1;
        }
      }

      // (char: ”-”) opt, digits, exponent opt.
      for (String d : signedDigits) {
        for (String e : signedDigits) {
          decimalNums[i] = d + "e" + e;
          i += 1;
        }
      }

      // (char: ”-”) opt, digits
      for (String d : signedDigits) {
        decimalNums[i] = d;
        i += 1;
      }

      String[] radix = new String[digits.length];
      i = 0;
      for (String d : digits) {
        radix[i] = d + "r";
        i += 1;
      }

      String[] extendedDigits = new String[] {"0", /* "A", "9", "Z", "AAA", "A09",*/ "04B", "CAFE", /*"BABE", "X33X56X",*/ "ZZ"};
      String[] radixNumbers = new String[radix.length * signs.length * extendedDigits.length * extendedDigits.length * signedDigits.length +
                                         radix.length * signs.length * extendedDigits.length * extendedDigits.length +
                                         radix.length * signs.length * extendedDigits.length * signedDigits.length +
                                         radix.length * signs.length * extendedDigits.length];

      i = 0;
      // radixNum = radix, (char: ”-”) opt, extendedDigits, extendedFraction opt, exponent opt.
      for (String r : radix) { for (String s : signs) { for (String d : extendedDigits) { for (String f : extendedDigits) { for (String e : signedDigits) {
        radixNumbers[i] = r + s + d + "." + f + "e" + e;
        i += 1;
      } } } } }

      // radixNum = radix, (char: ”-”) opt, extendedDigits, extendedFraction opt.
      for (String r : radix) { for (String s : signs) { for (String d : extendedDigits) { for (String f : extendedDigits) {
        radixNumbers[i] = r + s + d + "." + f;
        i += 1;
      } } } }

      // radixNum = radix, (char: ”-”) opt, extendedDigits, exponent opt.
      for (String r : radix) { for (String s : signs) { for (String d : extendedDigits) { for (String e : signedDigits) {
        radixNumbers[i] = r + s + d + "e" + e;
        i += 1;
      } } } }

      // radixNum = radix, (char: ”-”) opt, extendedDigits.
      for (String r : radix) { for (String s : signs) { for (String d : extendedDigits) {
        radixNumbers[i] = r + s + d;
        i += 1;
      } } }

      ArrayList<Object> list = new ArrayList<>();
      list.addAll(Arrays.asList(decimalNums));
      list.addAll(Arrays.asList(radixNumbers));

      return list;
    }

    private final String literal;

    public ValidNumerals(final String literal) {
      this.literal = literal;
    }

    @Test
    public void testNumeralLexing() {
      // add a space so that lexer stops lexing the numeral
      Lexer l = new Lexer(literal + " ");
      Assert.assertNotSame(Symbol.NONE, l.getSym());
      Assert.assertEquals(literal, l.getText());
    }
  }

  public static class InvalidNumerals {

    @Ignore("Can't parse this correctly, because we don't have the context in the lexer.")
    @Test
    public void testNumbersWithOperatorsMinus() {
      Lexer l = new Lexer("0-1 ");

      Assert.assertSame(Symbol.Numeral, l.getSym());
      Assert.assertEquals("0", l.getText());

      Assert.assertSame(Symbol.Minus, l.getSym());

      Assert.assertSame(Symbol.Numeral, l.getSym());
      Assert.assertEquals("1", l.getText());
    }

    @Test
    public void testNumbersWithOperatorsPlus() {
      Lexer l = new Lexer("0+1 ");

      Assert.assertSame(Symbol.Numeral, l.getSym());
      Assert.assertEquals("0", l.getText());

      Assert.assertSame(Symbol.Plus, l.getSym());

      Assert.assertSame(Symbol.Numeral, l.getSym());
      Assert.assertEquals("1", l.getText());
    }

    @Test
    public void testNumbersWithOperatorsMultiply() {
      Lexer l = new Lexer("0*1 ");

      Assert.assertSame(Symbol.Numeral, l.getSym());
      Assert.assertEquals("0", l.getText());

      Assert.assertSame(Symbol.Star, l.getSym());

      Assert.assertSame(Symbol.Numeral, l.getSym());
      Assert.assertEquals("1", l.getText());
    }
  }
}
