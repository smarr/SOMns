package dym;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Map.Entry;

import com.oracle.truffle.api.source.SourceSection;

import dym.profiles.AllocationProfile;
import dym.profiles.ArrayCreationProfile;
import dym.profiles.InvocationProfile;
import dym.profiles.MethodCallsiteProbe;


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
    methodCallsites();
    newObjectCount();
    newArrayCount();
  }

  private void methodActivations() {
    @SuppressWarnings("unchecked")
    Map<SourceSection, InvocationProfile> profiles = (Map<SourceSection, InvocationProfile>) data.get("methodInvocationProfile");

    try (PrintWriter file = new PrintWriter(metricsFolder + File.separator + "method-activations.csv")) {
      file.println("Source Identifier\tActivation Count");
      for (InvocationProfile p : profiles.values()) {
        file.print(p.getSourceSection().getIdentifier()); //TODO: probably need something more precise
        file.print("\t");
        file.println(p.getValue());
      }
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private void methodCallsites() {
    @SuppressWarnings("unchecked")
    Map<SourceSection, MethodCallsiteProbe> profiles = (Map<SourceSection, MethodCallsiteProbe>) data.get("methodCallsite");

    try (PrintWriter file = new PrintWriter(metricsFolder + File.separator + "method-callsites.csv")) {
      file.println("Source Section\tCall Count");
      for (MethodCallsiteProbe p : profiles.values()) {
        String abbrv = getSourceSectionAbbrv(p.getSourceSection());
        file.print(abbrv);
        file.print("\t");
        file.println(p.getValue());
      }
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private void newObjectCount() {
    @SuppressWarnings("unchecked")
    Map<SourceSection, AllocationProfile> profiles = (Map<SourceSection, AllocationProfile>) data.get("newObjectCount");

    try (PrintWriter file = new PrintWriter(metricsFolder + File.separator + "new-objects.csv")) {
      file.println("Source Section\tNew Objects\tNumber of Fields\tClass");

      for (AllocationProfile p : profiles.values()) {
        String abbrv = getSourceSectionAbbrv(p.getSourceSection());
        file.print(abbrv);
        file.print("\t");
        file.print(p.getValue());
        file.print("\t");
        file.print(p.getNumberOfObjectFields());
        file.print("\t");
        file.println(p.getSOMClass().getName().getString());
      }
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private void newArrayCount() {
    @SuppressWarnings("unchecked")
    Map<SourceSection, ArrayCreationProfile> profiles = (Map<SourceSection, ArrayCreationProfile>) data.get("newArrayCount");

    try (PrintWriter file = new PrintWriter(metricsFolder + File.separator + "new-arrays.csv")) {
      file.println("Source Section\tNew Arrays\tSize");

      for (ArrayCreationProfile p : profiles.values()) {
        String abbrv = getSourceSectionAbbrv(p.getSourceSection());
        for (Entry<Long, Long> e : p.getSizes().entrySet()) {
          file.print(abbrv);
          file.print("\t");
          file.print(e.getValue());
          file.print("\t");
          file.print(e.getKey());
          file.println();
        }
      }
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private static String getSourceSectionAbbrv(final SourceSection source) {
    String result;
    if (source.getSource() == null) {
      result = source.getShortDescription();
    } else {
      result = source.getSource().getShortName() + " pos=" + source.getCharIndex() + " len=" + source.getCharLength();
    }
    return result;
  }
}
