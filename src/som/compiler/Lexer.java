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
import java.lang.reflect.Field;

public final class Lexer {

  private static final String SEPARATOR = "----";
  private static final String PRIMITIVE = "primitive";

  private class LexerState {
    public LexerState() { }
    public LexerState(final LexerState old) {
      lineNumber = old.lineNumber;
      charsRead  = old.charsRead;
      buf        = old.buf;
      bufp       = old.bufp;
      sym        = old.sym;
      symc       = old.symc;
      text       = new StringBuffer(old.text);
      startCoord = old.startCoord;
    }
    private int                 lineNumber;
    private int                 charsRead; // all characters read, excluding the current line

    private String              buf;
    private int                 bufp;

    private Symbol              sym;
    private char                symc;
    private StringBuffer        text;

    private SourceCoordinate    startCoord;
  }

  private final BufferedReader infile;

  private boolean             peekDone;
  private LexerState          state;
  private LexerState          stateAfterPeek;

  private final Field nextCharField;

  protected Lexer(final Reader reader) {
    infile = new BufferedReader(reader);
    peekDone = false;
    state = new LexerState();
    state.buf = "";
    state.text = new StringBuffer();
    state.bufp = 0;
    state.lineNumber = 0;

    Field f = null;
    try {
      f = infile.getClass().getDeclaredField("nextChar");
      f.setAccessible(true);
    } catch (NoSuchFieldException | SecurityException e) {
      e.printStackTrace();
    }
    nextCharField = f;
  }

  public static final class SourceCoordinate {
    public final int startLine;
    public final int startColumn;
    public final int charIndex;

    public SourceCoordinate(final LexerState state) {
      this.startLine   = state.lineNumber;
      this.startColumn = state.bufp + 1;
      this.charIndex   = state.charsRead + state.bufp;
    }

    @Override
    public String toString() {
      return "SrcCoord(line: " + startLine + ", col: " + startColumn + ")";
    }
  }

  public SourceCoordinate getStartCoordinate() {
    return state.startCoord;
  }

  protected Symbol getSym() {
    if (peekDone) {
      peekDone = false;
      state = stateAfterPeek;
      stateAfterPeek = null;
      state.text = new StringBuffer(state.text);
      return state.sym;
    }

    state.startCoord = new SourceCoordinate(state);

    do {
      if (!hasMoreInput()) {
        state.sym = Symbol.NONE;
        state.symc = '\0';
        state.text = new StringBuffer(state.symc);
        return state.sym;
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
      if (bufchar(state.bufp + 1) == '=') {
        state.bufp += 2;
        state.sym = Symbol.Assign;
        state.symc = 0;
        state.text = new StringBuffer(":=");
      } else {
        state.bufp++;
        state.sym = Symbol.Colon;
        state.symc = ':';
        state.text = new StringBuffer(":");
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
      if (state.buf.startsWith(SEPARATOR, state.bufp)) {
        state.text = new StringBuffer();
        while (currentChar() == '-') {
          state.text.append(bufchar(state.bufp++));
        }
        state.sym = Symbol.Separator;
      } else {
        state.bufp++;
        state.sym = Symbol.Minus;
        state.symc = '-';
        state.text = new StringBuffer("-");
      }
    } else if (isOperator(currentChar())) {
      lexOperator();
    } else if (nextWordInBufferIs(PRIMITIVE)) {
      state.bufp += PRIMITIVE.length();
      state.sym = Symbol.Primitive;
      state.symc = 0;
      state.text = new StringBuffer(PRIMITIVE);
    } else if (Character.isLetter(currentChar())) {
      state.symc = 0;
      state.text = new StringBuffer();
      while (isIdentifierChar(currentChar())) {
        state.text.append(bufchar(state.bufp++));
      }
      state.sym = Symbol.Identifier;
      if (bufchar(state.bufp) == ':') {
        state.sym = Symbol.Keyword;
        state.bufp++;
        state.text.append(':');
        if (Character.isLetter(currentChar())) {
          state.sym = Symbol.KeywordSequence;
          while (Character.isLetter(currentChar()) || currentChar() == ':') {
            state.text.append(bufchar(state.bufp++));
          }
        }
      }
    } else if (Character.isDigit(currentChar())) {
      lexNumber();
    } else {
      state.sym = Symbol.NONE;
      state.symc = currentChar();
      state.text = new StringBuffer(state.symc);
    }

    return state.sym;
  }

  private void lexNumber() {
    state.sym = Symbol.Integer;
    state.symc = 0;
    state.text = new StringBuffer();

    boolean sawDecimalMark = false;

    do {
      state.text.append(bufchar(state.bufp++));

      if (!sawDecimalMark      &&
          '.' == currentChar() &&
          Character.isDigit(bufchar(state.bufp + 1))) {
        state.sym = Symbol.Double;
        state.text.append(bufchar(state.bufp++));
      }
    } while (Character.isDigit(currentChar()));
  }

  private void lexString() {
    state.sym = Symbol.STString;
    state.symc = 0;
    state.text = new StringBuffer();

    do {
      state.text.append(bufchar(++state.bufp));
    }
    while (currentChar() != '\'');

    state.text.deleteCharAt(state.text.length() - 1);
    state.bufp++;
  }

  private void lexOperator() {
    if (isOperator(bufchar(state.bufp + 1))) {
      state.sym = Symbol.OperatorSequence;
      state.symc = 0;
      state.text = new StringBuffer();
      while (isOperator(currentChar())) {
        state.text.append(bufchar(state.bufp++));
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
    LexerState old = new LexerState(state);
    if (peekDone) {
      throw new IllegalStateException("SOM lexer: cannot peek twice!");
    }
    getSym();
    Symbol nextSym = state.sym;
    stateAfterPeek = state;
    state = old;

    peekDone = true;
    return nextSym;
  }

  protected String getText() {
    return state.text.toString();
  }

  protected String getNextText() {
    return stateAfterPeek.text.toString();
  }

  protected String getRawBuffer() {
    return state.buf;
  }

  protected int getCurrentLineNumber() {
    return state.lineNumber;
  }

  protected int getCurrentColumn() {
    return state.bufp + 1;
  }

  // All characters read and processed, including current line
  protected int getNumberOfCharactersRead() {
    return state.startCoord.charIndex;
    //return state.charsRead + state.bufp;
  }

  private int fillBuffer() {
    try {
      if (!infile.ready()) { return -1; }

      // charsRead += buf.length();
      try {
        state.charsRead = nextCharField.getInt(infile);
      } catch (IllegalArgumentException | IllegalAccessException  e) {
        e.printStackTrace();
      }

      state.buf = infile.readLine();
      if (state.buf == null) { return -1; }
      ++state.lineNumber;
      state.bufp = 0;
      return state.buf.length();
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
      state.bufp++;
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
        state.bufp++;
        while (endOfBuffer()) {
          if (fillBuffer() == -1) { return; }
        }
      }
      while (currentChar() != '"');
      state.bufp++;
    }
  }

  private char currentChar() {
    return bufchar(state.bufp);
  }

  private boolean endOfBuffer() {
    return state.bufp >= state.buf.length();
  }

  private boolean isOperator(final char c) {
    return c == '~' || c == '&' || c == '|' || c == '*' || c == '/'
        || c == '\\' || c == '+' || c == '=' || c == '>' || c == '<'
        || c == ',' || c == '@' || c == '%';
  }

  private void match(final Symbol s) {
    state.sym = s;
    state.symc = currentChar();
    state.text = new StringBuffer("" + state.symc);
    state.bufp++;
  }

  private char bufchar(final int p) {
    return p >= state.buf.length() ? '\0' : state.buf.charAt(p);
  }

  private boolean isIdentifierChar(final char c) {
    return Character.isLetterOrDigit(c) || c == '_';
  }

  private boolean nextWordInBufferIs(final String text) {
    if (!state.buf.startsWith(text, state.bufp)) {
      return false;
    }
    return !isIdentifierChar(bufchar(state.bufp + text.length()));
  }

}
