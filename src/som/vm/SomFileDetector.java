package som.vm;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.spi.FileTypeDetector;

import som.interpreter.SomLanguage;


public final class SomFileDetector extends FileTypeDetector {

  @Override
  public String probeContentType(final Path path) throws IOException {
    if (path.getFileName().toString().endsWith(SomLanguage.DOT_FILE_EXTENSION)) {
      return SomLanguage.MIME_TYPE;
    }
    return null;
  }
}
