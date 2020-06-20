package tools.debugger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.oracle.svm.core.annotate.AutomaticFeature;

import tools.debugger.message.*;
import tools.debugger.message.Message.IncommingMessage;
import tools.debugger.message.Message.OutgoingMessage;
import tools.debugger.session.BreakpointInfo;
import tools.debugger.session.LineBreakpoint;
import tools.debugger.session.SectionBreakpoint;


/**
 * Registers classes that are necessary for Gson serialization/deserialization.
 * The classes are registered with SubstrateVM, and also provided as JsonProcessor.
 */
@AutomaticFeature
public class RuntimeReflectionRegistration implements Feature {

  private static class ClassGroup {
    private final Class<?> baseClass;
    private final String   typeMarker;

    private final boolean needInstantiation;

    private final LinkedHashMap<String, Class<?>> relevantSubclasses;

    ClassGroup(final Class<?> baseClass, final String typeMarker,
        final boolean needInstantiation) {
      this.baseClass = baseClass;
      this.typeMarker = typeMarker;
      this.needInstantiation = needInstantiation;
      this.relevantSubclasses = new LinkedHashMap<>();
    }

    public void register(final Class<?> klass) {
      relevantSubclasses.put(klass.getSimpleName(), klass);
    }

    public void register(final String name, final Class<?> klass) {
      relevantSubclasses.put(name, klass);
    }
  }

  private static final ClassGroup[] groups;

  static {
    ClassGroup outMsgs = new ClassGroup(OutgoingMessage.class, "type", false);
    outMsgs.register("source", SourceMessage.class);
    outMsgs.register(InitializationResponse.class);
    outMsgs.register(StoppedMessage.class);
    outMsgs.register(SymbolMessage.class);
    outMsgs.register(StackTraceResponse.class);
    outMsgs.register(ScopesResponse.class);
    outMsgs.register(VariablesResponse.class);
    outMsgs.register(ProgramInfoResponse.class);

    ClassGroup inMsgs = new ClassGroup(IncommingMessage.class, "action", true);
    inMsgs.register(InitializeConnection.class);
    inMsgs.register("updateBreakpoint", UpdateBreakpoint.class);
    inMsgs.register(StepMessage.class);
    inMsgs.register(StackTraceRequest.class);
    inMsgs.register(ScopesRequest.class);
    inMsgs.register(VariablesRequest.class);
    inMsgs.register(ProgramInfoRequest.class);
    inMsgs.register(TraceDataRequest.class);
    inMsgs.register("pauseActorMessageReceiver", PauseActorRequest.class);

    ClassGroup bps = new ClassGroup(BreakpointInfo.class, "type", true);
    bps.register(LineBreakpoint.class);
    bps.register(SectionBreakpoint.class);

    groups = new ClassGroup[3];
    groups[0] = outMsgs;
    groups[1] = inMsgs;
    groups[2] = bps;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Gson createJsonProcessor() {
    GsonBuilder builder = new GsonBuilder();

    for (ClassGroup group : groups) {
      ClassHierarchyAdapterFactory<?> af =
          new ClassHierarchyAdapterFactory<>(group.baseClass, group.typeMarker);

      for (Entry<String, Class<?>> e : group.relevantSubclasses.entrySet()) {
        af.register(e.getKey(), (Class) e.getValue());
      }

      builder.registerTypeAdapterFactory(af);
    }

    return builder.create();
  }

  private final HashSet<Class<?>> registeredClasses;

  public RuntimeReflectionRegistration() {
    registeredClasses = new HashSet<>();
  }

  public HashSet<Class<?>> getRegisteredClasses() {
    return registeredClasses;
  }

  public void register(final Class<?> klass, final boolean instantiable,
      final BeforeAnalysisAccess access) {
    if (registeredClasses.contains(klass)) {
      return;
    }

    registeredClasses.add(klass);

    // In unit tests, access is null
    if (access != null) {
      if (instantiable && !Modifier.isAbstract(klass.getModifiers()) && !klass.isEnum()) {
        RuntimeReflection.registerForReflectiveInstantiation(klass);
      }
      RuntimeReflection.register(klass);
      RuntimeReflection.register(klass.getDeclaredConstructors());
    }

    Class<?> superClass = klass.getSuperclass();
    if (isRelevant(superClass)) {
      register(superClass, false, access);
    }

    Field[] fields = klass.getDeclaredFields();
    if (access != null) {
      RuntimeReflection.register(fields);
    }

    if (!klass.isEnum()) {
      for (Field f : fields) {
        Class<?> fieldType = f.getType();

        if (isRelevant(fieldType)) {
          register(fieldType, instantiable, access);
          if (fieldType.isArray()) {
            register(fieldType.getComponentType(), instantiable, access);
          }
        }
      }
    }
  }

  private boolean isRelevant(final Class<?> klass) {
    if (klass == null || klass == Object.class || klass == Enum.class || klass == URI.class) {
      return false;
    }

    if (klass.isPrimitive() || klass.isArray() && klass.getComponentType().isPrimitive()) {
      return false;
    }

    return !isJavaLang(klass);
  }

  private boolean isJavaLang(final Class<?> klass) {
    if (klass.isArray()) {
      return isJavaLang(klass.getComponentType());
    }
    return klass.getPackage() != null && "java.lang".equals(klass.getPackage().getName());
  }

  @Override
  public void beforeAnalysis(final BeforeAnalysisAccess access) {
    for (ClassGroup group : groups) {
      register(group.baseClass, group.needInstantiation, access);

      for (Class<?> klass : group.relevantSubclasses.values()) {
        register(klass, group.needInstantiation, access);
      }
    }
  }
}
