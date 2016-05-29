package tools.dym;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.oracle.truffle.api.source.SourceSection;

import som.compiler.MixinDefinition;
import som.interpreter.Invokable;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.objectstorage.ClassFactory;
import som.vm.NotYetImplementedException;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;
import tools.dym.Tags.ArrayRead;
import tools.dym.Tags.ArrayWrite;
import tools.dym.Tags.OpArithmetic;
import tools.dym.Tags.OpClosureApplication;
import tools.dym.Tags.OpComparison;
import tools.dym.Tags.OpLength;
import tools.dym.Tags.StringAccess;
import tools.dym.profiles.AllocationProfile;
import tools.dym.profiles.Arguments;
import tools.dym.profiles.ArrayCreationProfile;
import tools.dym.profiles.BranchProfile;
import tools.dym.profiles.CallsiteProfile;
import tools.dym.profiles.Counter;
import tools.dym.profiles.InvocationProfile;
import tools.dym.profiles.LoopProfile;
import tools.dym.profiles.OperationProfile;
import tools.dym.profiles.ReadValueProfile;
import tools.dym.profiles.StructuralProbe;


public final class MetricsCsvWriter {

  private final Map<String, Map<SourceSection, ? extends JsonSerializable>> data;
  private final String metricsFolder;
  private final StructuralProbe structuralProbe; // TODO: not sure, we should probably not depend on the probe here
  private final int maxStackHeight;

  private MetricsCsvWriter(
      final Map<String, Map<SourceSection, ? extends JsonSerializable>> data,
      final String metricsFolder, final StructuralProbe probe, final int maxStackHeight) {
    this.data          = data;
    this.metricsFolder = metricsFolder;
    this.structuralProbe = probe;
    this.maxStackHeight = maxStackHeight;
  }

  public static void fileOut(
      final Map<String, Map<SourceSection, ? extends JsonSerializable>> data,
      final String metricsFolder,
      final StructuralProbe structuralProbe, // TODO: remove direct StructuralProbe passing hack
      final int maxStackHeight) {
    new MetricsCsvWriter(data, metricsFolder, structuralProbe, maxStackHeight).createCsvFiles();
  }

  private void createCsvFiles() {
    new File(metricsFolder).mkdirs();

    methodActivations();
    methodCallsites();
    newObjectCount();
    newArrayCount();
    fieldAccesses();
    localAccesses();
    usedClassesAndMethods();
    generalStats();
    branchProfiles();
    operationProfiles();
    loopProfiles();
  }

  private void generalStats() {
    try (PrintWriter file = new PrintWriter(metricsFolder + File.separator + "general-stats.csv")) {
      file.println("Max Stack Height");
      file.println(maxStackHeight);
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean hasTag(final Set<Class<?>> tags, final Class<?> tag) {
    for (Class<?> t : tags) {
      if (t == tag) {
        return true;
      }
    }
    return false;
  }

  private String typeCategory(final String typeName) {
    switch (typeName) {
      case "Integer":
        return "int";
      case "Double":
        return "float";
      case "String":
        return "str";
      case "Symbol":
        return "Symbol"; // TODO: keep this?
      case "Array":
        return "arr";
      case "True":
      case "False":
        return "bool";
      default:
        return "ref";
    }
  }

  private String operationType(final OperationProfile p, final Arguments a) {
    Set<Class<?>> tags = p.getTags();
    if (tags.contains(OpArithmetic.class)) {
      if (p.getOperation().equals("not")) {
        assert "bool".equals(typeCategory(a.getArgType(0)));
        assert "bool".equals(typeCategory(a.getArgType(1)));
        return "bool";
      } else if (p.getOperation().equals("abs") ||
          p.getOperation().equals("sqrt") ||
          p.getOperation().equals("sin") ||
          p.getOperation().equals("cos") ||
          p.getOperation().equals("asInteger")) {
        return typeCategory(a.getArgType(1));
      } else if (p.getOperation().equals("as32BitUnsignedValue") ||
          p.getOperation().equals("as32BitSignedValue")) {
        assert "int".equals(typeCategory(a.getArgType(0)));
        assert "int".equals(typeCategory(a.getArgType(1)));
        return "int";
      }

      String left  = typeCategory(a.getArgType(1));
      String right = typeCategory(a.getArgType(2));
      if (left.equals(right)) {
        return left;
      }
      String result = typeCategory(a.getArgType(0));
      if (result.equals(right)) {
        return right;
      } else if (result.equals(left)) {
        return left;
      }
      throw new NotYetImplementedException();
    } else if (tags.contains(OpComparison.class)) {
      String left  = typeCategory(a.getArgType(1));
      String right = typeCategory(a.getArgType(2));
      if (left.equals(right)) {
        return left;
      }
      if (left.equals("ref") || right.equals("ref")) {
        return "ref";
      }
      if (left.equals("float") && right.equals("int") ||
          left.equals("int") && right.equals("float")) {
        return "float";
      }
      throw new NotYetImplementedException();
    } else if (tags.contains(ArrayRead.class) || tags.contains(ArrayWrite.class)) {
      assert a.argTypeIs(1, "Array");
      assert a.argTypeIs(2, "Integer");
      if (tags.contains(ArrayRead.class)) {
        return typeCategory(a.getArgType(0));
      } else {
        return typeCategory(a.getArgType(3));
      }
    } else if (tags.contains(OpClosureApplication.class)) {
      return typeCategory(a.getArgType(0));
    } else if (tags.contains(OpLength.class)) {
      return typeCategory(a.getArgType(1));
    } else if (tags.contains(StringAccess.class)) {
      return "str";
    }
    throw new NotYetImplementedException();
  }

  private static String[] toNameArray(final Set<Class<?>> tags) {
    return tags.stream().map(c -> c.getSimpleName()).toArray(size -> new String[size]);
  }

  private void operationProfiles() {
    @SuppressWarnings("unchecked")
    Map<SourceSection, OperationProfile> ops = (Map<SourceSection, OperationProfile>) data.get(JsonWriter.OPERATIONS);
    try (CsvWriter file = new CsvWriter(metricsFolder, "operations.csv",
        "Source Section", "Operation", "Category", "Type", "Invocations")) {

      for (Entry<SourceSection, OperationProfile> e : ops.entrySet()) {
        for (Entry<Arguments, Integer> a : e.getValue().getArgumentTypes().entrySet()) {
          file.write(
              getSourceSectionAbbrv(e.getKey()),
              e.getValue().getOperation(),
              String.join(" ", toNameArray(e.getValue().getTags())),
              operationType(e.getValue(), a.getKey()),  // a.getKey().getOperationType()
              a.getValue());
        }

        file.write(
            getSourceSectionAbbrv(e.getKey()),
            e.getValue().getOperation(),
            String.join(" ", toNameArray(e.getValue().getTags())),
            "TOTAL",
            e.getValue().getValue());
      }
    }
  }

  private void methodActivations() {
    @SuppressWarnings("unchecked")
    Map<SourceSection, InvocationProfile> profiles = (Map<SourceSection, InvocationProfile>) data.get(JsonWriter.METHOD_INVOCATION_PROFILE);

    try (CsvWriter file = new CsvWriter(metricsFolder, "method-activations.csv",
        "Source Identifier", "Activation Count")) {
      for (InvocationProfile p : profiles.values()) {
        file.write(
            p.getMethod().getRootNode().getSourceSection().getIdentifier(), //TODO: probably need something more precise
            p.getValue());
      }
    }
  }

  private void methodCallsites() {
    @SuppressWarnings("unchecked")
    Map<SourceSection, CallsiteProfile> profiles = (Map<SourceSection, CallsiteProfile>) data.get(JsonWriter.METHOD_CALLSITE);

    try (CsvWriter file = new CsvWriter(metricsFolder, "method-callsites.csv",
        "Source Section", "Call Count", "Num Rcvrs", "Num Targets")) {
      for (CallsiteProfile p : profiles.values()) {
        if (data.get(JsonWriter.FIELD_READS).containsKey(p.getSourceSection()) ||
            data.get(JsonWriter.FIELD_WRITES).containsKey(p.getSourceSection()) ||
            data.get(JsonWriter.CLASS_READS).containsKey(p.getSourceSection())) {
          continue;  // filter out field reads, writes, and accesses to class objects
        }

        String abbrv = getSourceSectionAbbrv(p.getSourceSection());

        Map<ClassFactory, Integer> receivers = p.getReceivers();
//      int numRcvrsRecorded = receivers.values().stream().reduce(0, Integer::sum);
        Map<Invokable, Integer> calltargets = p.getCallTargets();
//      int numCalltargetsInvoked = calltargets.values().stream().reduce(0, Integer::sum);

        file.write(
            abbrv,
            p.getValue(),
            receivers.values().size(),
            calltargets.values().size());
      }
    }
  }

  private void newObjectCount() {
    @SuppressWarnings("unchecked")
    Map<SourceSection, AllocationProfile> profiles = (Map<SourceSection, AllocationProfile>) data.get(JsonWriter.NEW_OBJECT_COUNT);

    try (CsvWriter file = new CsvWriter(metricsFolder, "new-objects.csv",
        "Source Section", "New Objects", "Number of Fields", "Class")) {
      for (AllocationProfile p : profiles.values()) {
        String abbrv = getSourceSectionAbbrv(p.getSourceSection());
        file.write(abbrv, p.getValue(), p.getNumberOfObjectFields(), p.getTypeName());
      }
    }
  }

  private void newArrayCount() {
    @SuppressWarnings("unchecked")
    Map<SourceSection, ArrayCreationProfile> profiles = (Map<SourceSection, ArrayCreationProfile>) data.get(JsonWriter.NEW_ARRAY_COUNT);

    try (CsvWriter file = new CsvWriter(metricsFolder, "new-arrays.csv",
        "Source Section", "New Arrays", "Size")) {
      for (ArrayCreationProfile p : profiles.values()) {
        String abbrv = getSourceSectionAbbrv(p.getSourceSection());
        for (Entry<Long, Long> e : p.getSizes().entrySet()) {
          file.write(abbrv, e.getValue(), e.getKey());
        }
      }
    }
  }

  private void fieldAccesses() {
    @SuppressWarnings("unchecked")
    Map<SourceSection, ReadValueProfile> reads = (Map<SourceSection, ReadValueProfile>) data.get(JsonWriter.FIELD_READS);
    @SuppressWarnings("unchecked")
    Map<SourceSection, ReadValueProfile> writes = (Map<SourceSection, ReadValueProfile>) data.get(JsonWriter.FIELD_WRITES);

    try (CsvWriter file = new CsvWriter(metricsFolder, "field-accesses.csv",
        "Source Section", "Access Type", "Data Type", "Count")) {

      for (ReadValueProfile p : reads.values()) {
        String abbrv = getSourceSectionAbbrv(p.getSourceSection());
        for (Entry<ClassFactory, Integer> e : p.getTypeProfile().entrySet()) {
          file.write(abbrv, "read", e.getKey().getClassName().getString(), e.getValue());
        }

        file.write(abbrv, "read", "ALL", p.getValue());
      }

      for (Counter p : writes.values()) {
        String abbrv = getSourceSectionAbbrv(p.getSourceSection());
        file.write(abbrv, "write", "ALL", p.getValue());
      }
    }
  }

  private void localAccesses() {
    @SuppressWarnings("unchecked")
    Map<SourceSection, ReadValueProfile> reads = (Map<SourceSection, ReadValueProfile>) data.get(JsonWriter.LOCAL_READS);
    @SuppressWarnings("unchecked")
    Map<SourceSection, Counter> writes = (Map<SourceSection, Counter>) data.get(JsonWriter.LOCAL_WRITES);

    try (CsvWriter file = new CsvWriter(metricsFolder, "local-accesses.csv",
        "Source Section", "Access Type", "Data Type", "Count")) {
      for (ReadValueProfile p : reads.values()) {
        String abbrv = getSourceSectionAbbrv(p.getSourceSection());
        for (Entry<ClassFactory, Integer> e : p.getTypeProfile().entrySet()) {
          file.write(
              abbrv,
              "read",
              e.getKey().getClassName().getString(),
              e.getValue());
        }

        file.write(abbrv, "read", "ALL", p.getValue());
      }

      for (Counter p : writes.values()) {
        String abbrv = getSourceSectionAbbrv(p.getSourceSection());
        file.write(abbrv, "write", "ALL", p.getValue());
      }
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

    try (CsvWriter file = new CsvWriter(metricsFolder, "defined-classes.csv",
        "Class Name", "Source Section", "Methods Executed")) {

      for (MixinDefinition clazz : structuralProbe.getClasses()) {
        file.write(
            clazz.getName().getString(), // TODO: get fully qualified name
            getSourceSectionAbbrv(clazz.getSourceSection()),
            numExecutedMethods(clazz, profiles.values()));
      }
    }

    try (CsvWriter file = new CsvWriter(metricsFolder, "defined-methods.csv",
        "Name", "Executed", "Execution Count")) {

      for (SInvokable i : structuralProbe.getMethods()) {
        int numInvokations = methodInvocationCount(i, profiles.values());
        String executed = (numInvokations == 0) ? "false" : "true";
        file.write(i.toString(), executed, numInvokations);
      }
    }
  }

  private void branchProfiles() {
    @SuppressWarnings("unchecked")
    Map<SourceSection, BranchProfile> branches = (Map<SourceSection, BranchProfile>) data.get(JsonWriter.BRANCH_PROFILES);

    try (CsvWriter file = new CsvWriter(metricsFolder, "branches.csv",
        "Source Section", "TrueCnt", "FalseCnt", "Total")) {
      for (Entry<SourceSection, BranchProfile> e : branches.entrySet()) {
        file.write(
            getSourceSectionAbbrv(e.getKey()),
            e.getValue().getTrueCount(),
            e.getValue().getFalseCount(),
            e.getValue().getValue());
      }
    }
  }

  private void loopProfiles() {
    @SuppressWarnings("unchecked")
    Map<SourceSection, LoopProfile> loops = (Map<SourceSection, LoopProfile>) data.get(JsonWriter.LOOPS);

    try (CsvWriter file = new CsvWriter(metricsFolder, "loops.csv",
        "Source Section", "Loop Activations", "Num Iterations")) {

      for (Entry<SourceSection, LoopProfile> e : loops.entrySet()) {
        for (Entry<Integer, Integer> l : e.getValue().getIterations().entrySet()) {
          file.write(
              getSourceSectionAbbrv(e.getKey()),
              l.getKey(),
              l.getValue());
        }

        file.write(
            getSourceSectionAbbrv(e.getKey()),
            "TOTAL",
            e.getValue().getValue());
      }
    }
  }
}
