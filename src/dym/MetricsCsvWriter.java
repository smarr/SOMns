package dym;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Map;

import com.oracle.truffle.api.source.SourceSection;

import dym.profiles.InvocationProfile;


public final class MetricsCsvWriter {

  private final Map<String, Map<SourceSection, ? extends JsonSerializable>> data;
  private final String metricsFolder;

  private MetricsCsvWriter(
      final Map<String, Map<SourceSection, ? extends JsonSerializable>> data,
      final String metricsFolder) {
    this.data          = data;
    this.metricsFolder = metricsFolder;
  }

  public static void fileOut(
      final Map<String, Map<SourceSection, ? extends JsonSerializable>> data,
      final String metricsFolder) {
    new MetricsCsvWriter(data, metricsFolder).createCsvFiles();
  }

  private void createCsvFiles() {
    new File(metricsFolder).mkdirs();

    methodActivations();
  }

  private void methodActivations() {
    @SuppressWarnings("unchecked")
    Map<SourceSection, InvocationProfile> profiles = (Map<SourceSection, InvocationProfile>) data.get("methodInvocationProfile");

    try (PrintWriter file = new PrintWriter(metricsFolder + File.separator + "method-activations.csv")) {
      for (InvocationProfile p : profiles.values()) {
        file.print(p.getSourceSection().getIdentifier());
        file.print("\t");
        file.println(p.getValue());
      }
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
