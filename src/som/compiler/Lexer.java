/**
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

public final class Lexer {

  private static final String SEPARATOR = "----";
  private static final String PRIMITIVE = "primitive";

  private int                 lineNumber;
  private int                 charsRead; // all characters read, excluding the current line
  private BufferedReader      infile;
  private Symbol              sym;
  private char                symc;
  private StringBuffer        text;
  private boolean             peekDone;
  private Symbol              nextSym;
  private char                nextSymc;
  private StringBuffer        nextText;
  private String              buf;
  private int                 bufp;

  protected Lexer(final Reader reader) {
    infile = new BufferedReader(reader);
    peekDone = false;
    buf = "";
    text = new StringBuffer();
    bufp = 0;
    lineNumber = 0;
  }

  protected Symbol getSym() {
    if (peekDone) {
      peekDone = false;
      sym = nextSym;
      symc = nextSymc;
      text = new StringBuffer(nextText);
      return sym;
    }

    do {
      if (!hasMoreInput()) {
        sym = Symbol.NONE;
        symc = '\0';
        text = new StringBuffer(symc);
        return sym;
      }
      skipWhiteSpace();
      skipComment();
    }
    while (endOfBuffer() || Character.isWhitespace(currentChar())
        || currentChar() == '"');

    if (currentChar() == '\'') {
      lexString();
    } else if (currentChar() == '[') {
      match(Symbol.NewBlock);
    } else if (currentChar() == ']') {
      match(Symbol.EndBlock);
    } else if (currentChar() == ':') {
      if (bufchar(bufp + 1) == '=') {
        bufp += 2;
        sym = Symbol.Assign;
        symc = 0;
        text = new StringBuffer(":=");
      } else {
        bufp++;
        sym = Symbol.Colon;
        symc = ':';
        text = new StringBuffer(":");
      }
    } else if (currentChar() == '(') {
      match(Symbol.NewTerm);
    } else if (currentChar() == ')') {
      match(Symbol.EndTerm);
    } else if (currentChar() == '#') {
      match(Symbol.Pound);
    } else if (currentChar() == '^') {
      match(Symbol.Exit);
    } else if (currentChar() == '.') {
      match(Symbol.Period);
    } else if (currentChar() == '-') {
      if (buf.startsWith(SEPARATOR, bufp)) {
        text = new StringBuffer();
        while (currentChar() == '-') {
          text.append(bufchar(bufp++));
        }
        sym = Symbol.Separator;
      } else {
        bufp++;
        sym = Symbol.Minus;
        symc = '-';
        text = new StringBuffer("-");
      }
    } else if (isOperator(currentChar())) {
      lexOperator();
    } else if (buf.startsWith(PRIMITIVE, bufp)) {
      bufp += PRIMITIVE.length();
      sym = Symbol.Primitive;
      symc = 0;
      text = new StringBuffer(PRIMITIVE);
    } else if (Character.isLetter(currentChar())) {
      symc = 0;
      text = new StringBuffer();
      while (Character.isLetterOrDigit(currentChar()) || currentChar() == '_') {
        text.append(bufchar(bufp++));
      }
      sym = Symbol.Identifier;
      if (bufchar(bufp) == ':') {
        sym = Symbol.Keyword;
        bufp++;
        text.append(':');
        if (Character.isLetter(currentChar())) {
          sym = Symbol.KeywordSequence;
          while (Character.isLetter(currentChar()) || currentChar() == ':') {
            text.append(bufchar(bufp++));
          }
        }
      }
    } else if (Character.isDigit(currentChar())) {
      lexNumber();
    } else {
      sym = Symbol.NONE;
      symc = currentChar();
      text = new StringBuffer(symc);
    }

    return sym;
  }

  private void lexNumber() {
    sym = Symbol.Integer;
    symc = 0;
    text = new StringBuffer();

    boolean sawDecimalMark = false;

    do {
      text.append(bufchar(bufp++));

      if (!sawDecimalMark      &&
          '.' == currentChar() &&
          Character.isDigit(bufchar(bufp + 1))) {
        sym = Symbol.Double;
        text.append(bufchar(bufp++));
      }
    } while (Character.isDigit(currentChar()));
  }

  private void lexString() {
    sym = Symbol.STString;
    symc = 0;
    text = new StringBuffer();

    do {
      text.append(bufchar(++bufp));
    }
    while (currentChar() != '\'');

    text.deleteCharAt(text.length() - 1);
    bufp++;
  }

  private void lexOperator() {
    if (isOperator(bufchar(bufp + 1))) {
      sym = Symbol.OperatorSequence;
      symc = 0;
      text = new StringBuffer();
      while (isOperator(currentChar())) {
        text.append(bufchar(bufp++));
      }
    } else if (currentChar() == '~') {
      match(Symbol.Not);
    } else if (currentChar() == '&') {
      match(Symbol.And);
    } else if (currentChar() == '|') {
      match(Symbol.Or);
    } else if (currentChar() == '*') {
      match(Symbol.Star);
    } else if (currentChar() == '/') {
      match(Symbol.Div);
    } else if (currentChar() == '\\') {
      match(Symbol.Mod);
    } else if (currentChar() == '+') {
      match(Symbol.Plus);
    } else if (currentChar() == '=') {
      match(Symbol.Equal);
    } else if (currentChar() == '>') {
      match(Symbol.More);
    } else if (currentChar() == '<') {
      match(Symbol.Less);
    } else if (currentChar() == ',') {
      match(Symbol.Comma);
    } else if (currentChar() == '@') {
      match(Symbol.At);
    } else if (currentChar() == '%') {
      match(Symbol.Per);
    }
  }

  protected Symbol peek() {
    Symbol saveSym = sym;
    char saveSymc = symc;
    StringBuffer saveText = new StringBuffer(text);
    if (peekDone) {
      throw new IllegalStateException("SOM lexer: cannot peek twice!");
    }
    getSym();
    nextSym = sym;
    nextSymc = symc;
    nextText = new StringBuffer(text);
    sym = saveSym;
    symc = saveSymc;
    text = saveText;
    peekDone = true;
    return nextSym;
  }

  protected String getText() {
    return text.toString();
  }

  protected String getNextText() {
    return nextText.toString();
  }

  protected String getRawBuffer() {
    return buf;
  }

  protected int getCurrentLineNumber() {
    return lineNumber;
  }

  protected int getCurrentColumn() {
    return bufp + 1;
  }

  // All characters read and processed, including current line
  protected int getNumberOfCharactersRead() {
    return charsRead + bufp;
  }

  private int fillBuffer() {
    try {
      if (!infile.ready()) { return -1; }

      charsRead += buf.length();

      buf = infile.readLine();
      if (buf == null) { return -1; }
      ++lineNumber;
      bufp = 0;
      return buf.length();
    } catch (IOException ioe) {
      throw new IllegalStateException("Error reading from input: "
          + ioe.toString());
    }
  }

  private boolean hasMoreInput() {
    while (endOfBuffer()) {
      if (fillBuffer() == -1) {
        return false;
      }
    }
    return true;
  }

  private void skipWhiteSpace() {
    while (Character.isWhitespace(currentChar())) {
      bufp++;
      while (endOfBuffer()) {
        if (fillBuffer() == -1) {
          return;
        }
      }
    }
  }

  private void skipComment() {
    if (currentChar() == '"') {
      do {
        bufp++;
        while (endOfBuffer()) {
          if (fillBuffer() == -1) { return; }
        }
      }
      while (currentChar() != '"');
      bufp++;
    }
  }

  private char currentChar() {
    return bufchar(bufp);
  }

  private boolean endOfBuffer() {
    return bufp >= buf.length();
  }

  private boolean isOperator(final char c) {
    return c == '~' || c == '&' || c == '|' || c == '*' || c == '/'
        || c == '\\' || c == '+' || c == '=' || c == '>' || c == '<'
        || c == ',' || c == '@' || c == '%';
  }

  private void match(final Symbol s) {
    sym = s;
    symc = currentChar();
    text = new StringBuffer("" + symc);
    bufp++;
  }

  private char bufchar(final int p) {
    return p >= buf.length() ? '\0' : buf.charAt(p);
  }

}
