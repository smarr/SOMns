package tools.highlight;


public abstract class Tags {
  private Tags() { }

  public final class KeywordTag extends Tags {
    private KeywordTag() { }
  }

  public final class LiteralTag extends Tags {
    private LiteralTag() { }
  }

  public final class CommentTag extends Tags {
    private CommentTag() { }
  }

  public final class IdentifierTag extends Tags {
    private IdentifierTag() { }
  }

  public final class ArgumentTag extends Tags {
    private ArgumentTag() { }
  }

  public final class LocalVariableTag extends Tags {
    private LocalVariableTag() { }
  }

  public final class StatementSeparatorTag extends Tags {
    private StatementSeparatorTag() { }
  }

  public final class DelimiterOpeningTag extends Tags {
    private DelimiterOpeningTag() { }
  }

  public final class DelimiterClosingTag extends Tags {
    private DelimiterClosingTag() { }
  }
}
