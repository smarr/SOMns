package som;

import java.io.IOException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import som.interpreter.SomLanguage;
import som.interpreter.objectstorage.StorageAccessor;
import som.vm.VmSettings;
import tools.concurrency.TracingActors.ReplayActor;
import tools.concurrency.TracingBackend;
import tools.parser.KomposTraceParser;
import tools.snapshot.SnapshotBackend;


public final class Launcher {

  /** This source is a marker to start execution based on the arguments provided. */
  public static final Source START = createMarkerSource(SomLanguage.START_SOURCE);

  /** This source is a marker to initialize the {@link Context}, but nothing else. */
  public static final Source INIT = createMarkerSource(SomLanguage.INIT_SOURCE);

  /**
   * This source is a marker to shutdown the {@link Context}, which is needed to avoid races.
   */
  public static final Source SHUTDOWN = createMarkerSource(SomLanguage.SHUTDOWN_SOURCE);

  /** Standard code for exiting with an error. */
  public static final int EXIT_WITH_ERROR = 1;

  public static void main(final String[] args) {
    StorageAccessor.initAccessors();

    Builder builder = createContextBuilder(args);
    Context context = builder.build();

    int exitCode = 0;
    try {
      Value result = context.eval(START);
      exitCode = result.as(Integer.class);
    } finally {
      context.eval(SHUTDOWN);
      context.close();
      finalizeExecution(exitCode);
    }

    // TODO: TruffleException has a way to communicate exit code
    System.exit(exitCode);
  }

  private static void finalizeExecution(final int exitCode) {
    TracingBackend.waitForTrace();
    if (VmSettings.SNAPSHOTS_ENABLED && !VmSettings.TEST_SNAPSHOTS) {
      SnapshotBackend.writeSnapshot();
    }

    if (exitCode != 0) {
      ReplayActor.printMissingMessages();
    }

    if (VmSettings.MEMORY_TRACING) {
      TracingBackend.reportPeakMemoryUsage();
    }

    // Note: Kompos Trace is parsed right after writing it
    // to produce the list of messages on the erroneous path.
    // Could be done at the beginning of assisted debugging.
    if (VmSettings.KOMPOS_TRACING) {
      KomposTraceParser tp = new KomposTraceParser();
      tp.createStackTraceFile(VmSettings.TRACE_FILE);
    }
  }

  public static Builder createContextBuilder(final String[] args) {
    Builder builder = Context.newBuilder(SomLanguage.LANG_ID).in(System.in).out(System.out)
                             .allowAllAccess(true).arguments(SomLanguage.LANG_ID, args);
    return builder;
  }

  private static Source createMarkerSource(final String marker) {
    try {
      return Source.newBuilder(SomLanguage.LANG_ID, marker, marker).internal(true).build();
    } catch (IOException e) {
      // should never happen
      throw new RuntimeException(e);
    }
  }
}
