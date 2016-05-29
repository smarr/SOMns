package tools.dym;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;


public class CsvWriter implements AutoCloseable {

  private final PrintWriter writer;
  private final String[] columns;

  public CsvWriter(final String folder, final String fileName, final String... columns) {
    if (columns.length < 1) {
      throw new IllegalArgumentException("No column header provided");
    }

    this.columns = columns;
    try {
      writer = new PrintWriter(folder + File.separator + fileName);
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
    write((Object[]) columns);
  }

  public void write(final Object... columns) {
    if (columns.length != this.columns.length) {
      throw new IllegalArgumentException(
          "Provided row has only " + columns.length + " columns, while "
               + this.columns.length + " are expected.");
    }
    for (int i = 0; i < columns.length; i++) {
      if (i != 0) {
        writer.print("\t");
      }
      writer.print(columns[i]);
    }
    writer.println();
  }

  @Override
  public void close() {
    writer.close();
  }
}
