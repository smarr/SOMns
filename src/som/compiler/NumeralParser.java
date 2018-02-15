package som.compiler;

import static som.compiler.Lexer.isDigit;
import static som.compiler.Lexer.isUppercaseLetter;

import java.math.BigInteger;

import som.Output;


public class NumeralParser {

  private final Lexer   lexer;
  private StringBuilder mainDigits;
  private StringBuilder radix;
  private StringBuilder exponent;
  private int           dotIndex;
  private boolean       isNegative;
  private boolean       hasFraction;
  private boolean       exponentIsNegative;

  public NumeralParser(final Lexer lexer) {
    this.lexer = lexer;
  }

  public boolean isInteger() {
    return !hasFraction && exponent == null;
  }

  public double getDouble() {
    assert hasFraction || exponent != null;

    if (radix != null) {
      return calculateNumberWithRadixDouble(dotIndex);
    } else {
      double result = Double.parseDouble(mainDigits.toString());

      if (isNegative) {
        result = result * -1;
      }

      if (exponent != null) {
        long exp = Long.parseLong(exponent.toString());
        if (exponentIsNegative) {
          exp = exp * -1;
        }
        result = result * Math.pow(10, exp);
      }
      return result;
    }
  }

  public Number getInteger() {
    assert !hasFraction;
    assert exponent == null;

    if (radix != null) {
      return calculateNumberWithRadixLong();
    } else {
      String digits = mainDigits.toString();
      try {
        long result = Long.parseLong(digits);
        if (isNegative) {
          result = result * -1;
        }

        return result;
      } catch (NumberFormatException e) {
        BigInteger result = new BigInteger(digits);
        if (isNegative) {
          result = result.negate();
        }
        return result;
      }
    }
  }

  private long calculateNumberWithRadixLong() {
    int r = Integer.parseInt(radix.toString());
    long result = 0;
    int length = mainDigits.length();

    for (int i = 0; i < length; i += 1) {
      char c = mainDigits.charAt(i);
      int v;
      if (isDigit(c)) {
        v = c - '0';
      } else {
        assert isUppercaseLetter(c);
        v = c - 'A' + 10 /* A has value 10 */;
      }

      try {
        result = Math.addExact(result, (long) (Math.pow(r, length - i - 1) * v));
      } catch (ArithmeticException e) {
        // TODO: need to overflow into BigInteger
        Output.errorPrintln("Warning: Parsed Integer literal which did not fit into long. "
            + lexer.getCurrentLineNumber() + ":" + lexer.getCurrentColumn());
        return result;
      }
    }
    return result;
  }

  private double calculateNumberWithRadixDouble(final int dot) {
    int dotOffset = 1; // account for indexing oddities
    int r = Integer.parseInt(radix.toString());
    double result = 0.0;
    int length = mainDigits.length();

    for (int i = 0; i < length; i += 1) {
      if (i == dot) {
        dotOffset = 0;
        continue;
      }

      char c = mainDigits.charAt(i);
      int v;
      if (isDigit(c)) {
        v = c - '0';
      } else {
        assert isUppercaseLetter(c);
        v = c - 'A' + 10 /* A has value 10 */;
      }

      result += Math.pow(r, dot - i - dotOffset) * v;
    }
    return result;
  }

  public void parse() {
    boolean initialSign = false;
    if (lexer.currentChar() == '-') {
      lexer.acceptChar();
      initialSign = true;
      isNegative = true;
    }

    StringBuilder firstDigits = new StringBuilder();
    lexDigits(firstDigits);

    // if there was a sign at the start, we can't parse a radix
    if (!initialSign && lexer.currentChar() == 'r') {
      radix = firstDigits;
      lexRadixNum();
    } else {
      mainDigits = firstDigits;
      if (lexer.currentChar() == '.' && isDigit(lexer.nextChar())) {
        dotIndex = firstDigits.length();
        hasFraction = true;
        lexFraction(firstDigits);
      }

      if (lexer.currentChar() == 'e' && nextIsExponent()) {
        exponent = new StringBuilder();
        lexExponent(exponent);
      }
    }
  }

  private boolean nextIsExponent() {
    char next = lexer.nextChar();
    if (isDigit(next)) {
      return true;
    }

    return next == '-' && isDigit(lexer.nextChar(2));
  }

  private void lexDigits(final StringBuilder digits) {
    while (isDigit(lexer.currentChar())) {
      digits.append(lexer.acceptChar());
    }
  }

  private void lexExtendedDigits(final StringBuilder digits) {
    do {
      digits.append(lexer.acceptChar());
    } while (isDigit(lexer.currentChar()) || isUppercaseLetter(lexer.currentChar()));
  }

  private void lexRadixNum() {
    lexer.acceptChar();

    if (lexer.currentChar() == '-') {
      isNegative = true;
      lexer.acceptChar();
    }

    mainDigits = new StringBuilder();
    lexExtendedDigits(mainDigits);

    if (lexer.currentChar() == '.' &&
        (isDigit(lexer.nextChar()) || isUppercaseLetter(lexer.nextChar()))) {
      dotIndex = mainDigits.length();
      hasFraction = true;
      lexExtendedFraction(mainDigits);
    }

    if (lexer.currentChar() == 'e' && nextIsExponent()) {
      exponent = new StringBuilder();
      lexExponent(exponent);
    }
  }

  private void lexFraction(final StringBuilder numeral) {
    numeral.append(lexer.acceptChar());
    lexDigits(numeral);
  }

  private void lexExtendedFraction(final StringBuilder numeral) {
    numeral.append(lexer.acceptChar());
    lexExtendedDigits(numeral);
  }

  private void lexExponent(final StringBuilder exponent) {
    lexer.acceptChar();

    if (lexer.currentChar() == '-') {
      lexer.acceptChar();
      exponentIsNegative = true;
    }

    lexDigits(exponent);
  }
}
