/**
 * Copyright (c) 2016-2017 Stefan Marr
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
package tools.debugger;

import com.oracle.truffle.api.source.SourceSection;


public abstract class Tags {
  private Tags() { }

  /**
   * Marks keywords or keyword-like language constructs.
   * Elements that are not preserved in the AST should reported via
   * {@link VM#reportSyntaxElement(Class, SourceSection)}.
   */
  public final class KeywordTag extends Tags {
    private KeywordTag() { }
  }

  /**
   * Marks literal values.
   */
  public final class LiteralTag extends Tags {
    private LiteralTag() { }
  }

  /**
   * Marks comments.
   * Elements that are not preserved in the AST should reported via
   * {@link VM#reportSyntaxElement(Class, SourceSection)}.
   */
  public final class CommentTag extends Tags {
    private CommentTag() { }
  }

  /**
   * Marks identifiers. Currently only used for class names.
   * TODO: figure out whether this is really useful.
   * Elements that are not preserved in the AST should reported via
   * {@link VM#reportSyntaxElement(Class, SourceSection)}.
   */
  public final class IdentifierTag extends Tags {
    private IdentifierTag() { }
  }

  /**
   * Marks formal and actual arguments.
   * Formal arguments that are not preserved in the AST should reported via
   * {@link VM#reportSyntaxElement(Class, SourceSection)}.
   */
  public final class ArgumentTag extends Tags {
    private ArgumentTag() { }
  }

  /**
   * Marks local variables.
   * Variable declarations that are not part of the AST should be reported via
   * {@link VM#reportSyntaxElement(Class, SourceSection)}.
   */
  public final class LocalVariableTag extends Tags {
    private LocalVariableTag() { }
  }

  /**
   * Marks line endings like ';', or '.'.
   * Elements that are not preserved in the AST should reported via
   * {@link VM#reportSyntaxElement(Class, SourceSection)}.
   */
  public final class StatementSeparatorTag extends Tags {
    private StatementSeparatorTag() { }
  }

  /**
   * Marks opening delimiters such as parentheses.
   * Elements that are not preserved in the AST should reported via
   * {@link VM#reportSyntaxElement(Class, SourceSection)}.
   */
  public final class DelimiterOpeningTag extends Tags {
    private DelimiterOpeningTag() { }
  }

  /**
   * Marks closing delimiters such as parentheses.
   * Elements that are not preserved in the AST should reported via
   * {@link VM#reportSyntaxElement(Class, SourceSection)}.
   */
  public final class DelimiterClosingTag extends Tags {
    private DelimiterClosingTag() { }
  }
}
