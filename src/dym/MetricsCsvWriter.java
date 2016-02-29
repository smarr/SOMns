package dym;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import som.compiler.MixinDefinition;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.source.SourceSection;

import dym.profiles.AllocationProfile;
import dym.profiles.ArrayCreationProfile;
import dym.profiles.InvocationProfile;
import dym.profiles.CallsiteProfile;
import dym.profiles.ReadValueProfile;
import dym.profiles.StructuralProbe;


public final class MetricsCsvWriter {

  private final Map<String, Map<SourceSection, ? extends JsonSerializable>> data;
  private final String metricsFolder;
  private final StructuralProbe structuralProbe; // TODO: not sure, we should probably not depend on the probe here

  private MetricsCsvWriter(
      final Map<String, Map<SourceSection, ? extends JsonSerializable>> data,
      final String metricsFolder, final StructuralProbe probe) {
    this.data          = data;
    this.metricsFolder = metricsFolder;
    this.structuralProbe = probe;
  }

  public static void fileOut(
      final Map<String, Map<SourceSection, ? extends JsonSerializable>> data,
      final String metricsFolder,
      final StructuralProbe structuralProbe) { // TODO: remove direct StructuralProbe passing hack
    new MetricsCsvWriter(data, metricsFolder, structuralProbe).createCsvFiles();
  }

  private void createCsvFiles() {
    new File(metricsFolder).mkdirs();

    methodActivations();
    methodCallsites();
    newObjectCount();
    newArrayCount();
    fieldReads();
    localReads();
    usedClassesAndMethods();
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
    Map<SourceSection, CallsiteProfile> profiles = (Map<SourceSection, CallsiteProfile>) data.get("methodCallsite");


    try (PrintWriter file = new PrintWriter(metricsFolder + File.separator + "method-callsites.csv")) {
      file.println("Source Section\tCall Count");
      for (CallsiteProfile p : profiles.values()) {
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

  private void fieldReads() {
    @SuppressWarnings("unchecked")
    Map<SourceSection, ReadValueProfile> profiles = (Map<SourceSection, ReadValueProfile>) data.get("fieldReads");

    try (PrintWriter file = new PrintWriter(metricsFolder + File.separator + "field-reads.csv")) {
      file.println("Source Section\tRead Type\tCount");

      for (ReadValueProfile p : profiles.values()) {
        String abbrv = getSourceSectionAbbrv(p.getSourceSection());
        for (Entry<SClass, Integer> e : p.getTypeProfile().entrySet()) {
          file.print(abbrv);
          file.print("\t");
          file.print(e.getKey().getName().getString());
          file.print("\t");
          file.print(e.getValue());
          file.println();
        }
      }
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private void localReads() {
    @SuppressWarnings("unchecked")
    Map<SourceSection, ReadValueProfile> profiles = (Map<SourceSection, ReadValueProfile>) data.get("localReads");

    try (PrintWriter file = new PrintWriter(metricsFolder + File.separator + "local-reads.csv")) {
      file.println("Source Section\tRead Type\tCount");

      for (ReadValueProfile p : profiles.values()) {
        String abbrv = getSourceSectionAbbrv(p.getSourceSection());
        for (Entry<SClass, Integer> e : p.getTypeProfile().entrySet()) {
          file.print(abbrv);
          file.print("\t");
          file.print(e.getKey().getName().getString());
          file.print("\t");
          file.print(e.getValue());
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

  private int methodInvocationCount(final SInvokable method, final Collection<InvocationProfile> profiles) {
    InvocationProfile profile = null;

    for (InvocationProfile p : profiles) {
      if (p.getMethod() == method.getInvokable()) {
        profile = p;
        break;
      }
    }

    if (profile == null) {
      return 0;
    } else {
      return profile.getValue();
    }
  }

  private int numExecutedMethods(final MixinDefinition mixin, final Collection<InvocationProfile> profiles) {
    int numMethodsExecuted = 0;
    Map<SSymbol, Dispatchable> disps = mixin.getInstanceDispatchables();
    for (Dispatchable d : disps.values()) {
      if (d instanceof SInvokable) {
        int invokeCount = methodInvocationCount(((SInvokable) d), profiles);
        if (invokeCount > 0) {
          numMethodsExecuted += 1;
        }
      }
    }
    return numMethodsExecuted;
  }

  private void usedClassesAndMethods() {
    @SuppressWarnings("unchecked")
    Map<SourceSection, InvocationProfile> profiles = (Map<SourceSection, InvocationProfile>) data.get(JsonWriter.METHOD_INVOCATION_PROFILE);

    try (PrintWriter file = new PrintWriter(metricsFolder + File.separator + "defined-classes.csv")) {
      file.println("Class Name\tMethods Executed");

      for (MixinDefinition clazz : structuralProbe.getClasses()) {
        file.print(clazz.getName().getString());
        // TODO: get fully qualified name
        file.print("\t");
        file.print(numExecutedMethods(clazz, profiles.values()));
        file.println();
      }
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }


    try (PrintWriter file = new PrintWriter(metricsFolder + File.separator + "defined-methods.csv")) {
      file.println("Name\tExecuted\tExecution Count");

      for (SInvokable i : structuralProbe.getMethods()) {
        file.print(i.toString());
        file.print("\t");

        int numInvokations = methodInvocationCount(i, profiles.values());

        if (numInvokations == 0) {
          file.println("false\t0");
        } else {
          file.print("true\t");
          file.println(numInvokations);
        }
      }
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
