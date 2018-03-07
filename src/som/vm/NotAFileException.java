package som.vm;

import java.io.IOException;


public final class NotAFileException extends IOException {
  private static final long serialVersionUID = -8754495184109566069L;

  public NotAFileException(final String path) {
    super(path);
  }
}
