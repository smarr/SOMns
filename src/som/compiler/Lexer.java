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

  public static class Peek {
    public Peek(final Symbol sym, final String text) {
      nextSym  = sym;
      nextText = text;
    }
    public final Symbol nextSym;
    public final String nextText;
  }

  private static class LexerState {
    LexerState() { }
    LexerState(final LexerState old) {
      lineNumber = old.lineNumber;
      charsRead  = old.charsRead;
      lastNonWhiteCharIdx = old.lastNonWhiteCharIdx;
      buf        = old.buf;
      bufPointer = old.bufPointer;
      sym        = old.sym;
      symc       = old.symc;
      text       = new StringBuffer(old.text);
      startCoord = old.startCoord;
    }

    public void set(final Symbol sym, final char symChar, final String text) {
      this.sym  = sym;
      this.symc = symChar;
      this.text = new StringBuffer(text);
    }

    public void set(final Symbol sym) {
      this.sym = sym;
      this.symc = 0;
      this.text = new StringBuffer();
    }

    private int                 lineNumber;
    private int                 charsRead; // all characters read, excluding the current line
    private int                 lastNonWhiteCharIdx;

    private String              buf;
    private int                 bufPointer;

    private Symbol              sym;
    private char                symc;
    private StringBuffer        text;

    private SourceCoordinate    startCoord;

    int incPtr() {
      int cur = bufPointer;
      bufPointer += 1;
      lastNonWhiteCharIdx = charsRead + bufPointer;
      return cur;
    }

    int incPtr(final int val) {
      int cur = bufPointer;
      bufPointer += val;
      lastNonWhiteCharIdx = charsRead + bufPointer;
      return cur;
    }
  }

  private final BufferedReader infile;

  private boolean             peekDone;
  private LexerState          state;
  private LexerState          stateAfterPeek;

  private final Field nextCharField;

  protected Lexer(final Reader reader, final long fileSize) {
    /** TODO: we rely on the internal implementation to get the position in the
     *        file. This should be fixed and reimplemented to avoid such hacks.
     *        We need to know for Truffle the character index of a token,
     *        but we do not get it because line-based reading does split the
     *        type of end-of-line indicator and its length.
     */
    infile = new BufferedReader(reader, (int) fileSize);
    peekDone = false;
    state = new LexerState();
    state.buf = "";
    state.text = new StringBuffer();
    state.bufPointer = 0;
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
    public final int lastNonWhiteIdx;

    public SourceCoordinate(final LexerState state) {
      this.startLine   = state.lineNumber;
      this.startColumn = state.bufPointer + 1;
      this.charIndex   = state.charsRead + state.bufPointer;
      this.lastNonWhiteIdx = state.lastNonWhiteCharIdx;
      assert startLine   >= 0;
      assert startColumn >= 0;
      assert charIndex   >= 0;
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

    do {
      if (!hasMoreInput()) {
        state.set(Symbol.NONE);
        return state.sym;
      }
      skipWhiteSpace();
    }
    while (endOfBuffer() || Character.isWhitespace(currentChar()));

    state.startCoord = new SourceCoordinate(state);

    if (currentChar() == '\'') {
      lexString();
    } else if (currentChar() == '[') {
      match(Symbol.NewBlock);
    } else if (currentChar() == ']') {
      match(Symbol.EndBlock);
    } else if (currentChar() == ':') {
      if (nextChar() == '=') {
        state.incPtr(2);
        state.set(Symbol.Assign, '\0', ":=");
      } else if (nextChar() == ':') {
        state.incPtr();
        if (nextChar() == '=') { // a little hack to have a double peek...
          state.incPtr(2);
          state.set(Symbol.SlotMutableAssign, '\0', "::=");
        } else {
          state.set(Symbol.Colon, ':', ":");
        }
      } else {
        match(Symbol.Colon);
      }
    } else if (currentChar() == '(') {
      if (nextChar() == '*') {
        state.incPtr(2);
        state.set(Symbol.BeginComment, '\0', "(*");
      } else {
        match(Symbol.NewTerm);
      }
    } else if (currentChar() == '*' && nextChar() == ')') {
      state.incPtr(2);
      state.set(Symbol.EndComment, '\0', "*)");
    } else if (currentChar() == ')') {
      match(Symbol.EndTerm);
    } else if (currentChar() == '#') {
      match(Symbol.Pound);
    } else if (currentChar() == '^') {
      match(Symbol.Exit);
    } else if (currentChar() == '.') {
      match(Symbol.Period);
    } else if (currentChar() == '-') {
      match(Symbol.Minus);
    } else if (currentChar() == '<') {
      state.incPtr();
      if (currentChar() == ':') {
        state.incPtr();
        state.set(Symbol.MixinOperator, '\0', "<:");
      } else if (currentChar() == '-' && nextChar() == ':') {
        state.incPtr(2);
        state.set(Symbol.EventualSend, '\0', "<-:");
      } else {
        assert state.bufPointer != 0; // this case is not supported currently
        state.bufPointer -= 1;
        lexOperator(); // can't just lex '<' here, because we need to lex '<>' as operator sequence.
      }
    } else if (isOperator(currentChar())) {
      lexOperator();
    } else if (Character.isLetter(currentChar())) {
      state.set(Symbol.Identifier);
      while (isIdentifierChar(currentChar())) {
        state.text.append(bufchar(state.incPtr()));
      }
      if (currentChar() == ':') {
        state.sym = Symbol.Keyword;
        state.incPtr();
        state.text.append(':');
        if (Character.isLetter(currentChar())) {
          state.sym = Symbol.KeywordSequence;
          while (Character.isLetter(currentChar()) || currentChar() == ':') {
            state.text.append(bufchar(state.incPtr()));
          }
        }
      }
    } else if (Character.isDigit(currentChar())) {
      lexNumber();
    } else {
      state.set(Symbol.NONE, currentChar(), "" + currentChar());
      state.bufPointer++;
    }

    return state.sym;
  }

  private void lexNumber() {
    state.set(Symbol.Integer);

    boolean sawDecimalMark = false;

    do {
      state.text.append(bufchar(state.incPtr()));

      if (!sawDecimalMark      &&
          '.' == currentChar() &&
          Character.isDigit(nextChar())) {
        state.sym = Symbol.Double;
        state.text.append(bufchar(state.incPtr()));
      }
    } while (Character.isDigit(currentChar()));
  }

  private void lexEscapeChar() {
    assert !endOfBuffer();

    char current = currentChar();
    switch (current) {
      case 't': state.text.append("\t"); break;
      case 'b': state.text.append("\b"); break;
      case 'n': state.text.append("\n"); break;
      case 'r': state.text.append("\r"); break;
      case 'f': state.text.append("\f"); break;
      case '\'': state.text.append("'"); break;
      case '\\': state.text.append("\\"); break;
    }
    state.incPtr();
  }

  private void lexStringChar() {
    if (currentChar() == '\\') {
      state.incPtr();
      lexEscapeChar();
    } else {
      state.text.append(currentChar());
      state.incPtr();
    }
  }

  private void lexString() {
    state.set(Symbol.STString);
    state.incPtr();

    while (currentChar() != '\'') {
      lexStringChar();
      while (endOfBuffer()) {
        if (fillBuffer() == -1) {
          return;
        }
      }
    }

    state.incPtr();
  }

  private void lexOperator() {
    if (isOperator(nextChar())) {
      state.set(Symbol.OperatorSequence);
      while (isOperator(currentChar())) {
        state.text.append(bufchar(state.incPtr()));
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

  protected Peek peek() {
    LexerState old = new LexerState(state);
    if (peekDone) {
      throw new IllegalStateException("SOM lexer: cannot peek twice!");
    }
    getSym();
    Peek peek = new Peek(state.sym, state.text.toString());

    stateAfterPeek = state;
    state = old;

    peekDone = true;
    return peek;
  }

  protected String getText() {
    return state.text.toString();
  }

  protected String getRawBuffer() {
    return state.buf;
  }

  protected int getCurrentLineNumber() {
    return state.lineNumber;
  }

  protected int getCurrentColumn() {
    return state.bufPointer + 1;
  }

  protected int getNumberOfNonWhiteCharsRead() {
    return state.startCoord.lastNonWhiteIdx;
  }

  // All characters read and processed, including current line
  protected int getNumberOfCharactersRead() {
    return state.startCoord.charIndex;
  }

  private int fillBuffer() {
    try {
      if (!infile.ready()) { return -1; }

      try {
        int charsRead = nextCharField.getInt(infile);
        assert charsRead >= 0 && charsRead >= state.charsRead;
        state.charsRead = charsRead;
      } catch (IllegalArgumentException | IllegalAccessException  e) {
        e.printStackTrace();
      }

      state.buf = infile.readLine();
      if (state.buf == null) { return -1; }
      ++state.lineNumber;
      state.bufPointer = 0;
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

  protected String getCommentPart() {
    // it ends with either a new comment starting '(*' or the original comment
    // ending with '*)'
    StringBuilder comment = new StringBuilder();
    boolean commentPartEnded = false;

    while (!commentPartEnded) {
      char current = currentChar();
      commentPartEnded = (current == '(' && nextChar() == '*')
                      || (current == '*' && nextChar() == ')');
      if (commentPartEnded) {
        return comment.toString();
      }
      comment.append(current);
      state.incPtr();

      while (endOfBuffer()) {
        comment.append('\n');
        if (fillBuffer() == -1) {
          return comment.toString();
        }
      }
    }
    return comment.toString();
  }

  private void skipWhiteSpace() {
    while (Character.isWhitespace(currentChar())) {
      state.bufPointer++;
      while (endOfBuffer()) {
        if (fillBuffer() == -1) {
          return;
        }
      }
    }
  }

  private char currentChar() {
    return bufchar(state.bufPointer);
  }

  protected char nextChar() {
    return bufchar(state.bufPointer + 1);
  }

  private boolean endOfBuffer() {
    return state.bufPointer >= state.buf.length();
  }

  private boolean isOperator(final char c) {
    return c == '~' || c == '&' || c == '|' || c == '*' || c == '/'
        || c == '\\' || c == '+' || c == '=' || c == '>' || c == '<'
        || c == ',' || c == '@' || c == '%';
  }

  private void match(final Symbol s) {
    state.set(s, currentChar(), "" + currentChar());
    state.incPtr();
  }

  private char bufchar(final int p) {
    return p >= state.buf.length() ? '\0' : state.buf.charAt(p);
  }

  private boolean isIdentifierChar(final char c) {
    return Character.isLetterOrDigit(c) || c == '_';
  }
}
