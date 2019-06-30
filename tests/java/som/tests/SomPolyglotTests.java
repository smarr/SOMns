package som.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collection;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.CPUSampler.Payload;
import com.oracle.truffle.tools.profiler.ProfilerNode;

import som.Launcher;
import som.VM;
import som.interpreter.SomLanguage;
import som.interpreter.objectstorage.StorageAccessor;


public class SomPolyglotTests {

  Context context;

  @BeforeClass
  public static void setup() {
    // Needed to be able to execute SOMns initialization
    StorageAccessor.initAccessors();
  }

  @After
  public void resetObjectSystem() {
    VM.resetClassReferences(true);
    if (context != null) {
      context.eval(Launcher.SHUTDOWN);
      context.close();
    }
  }

  @Test
  public void engineKnowsSomLanguage() {
    Context vm = Context.newBuilder().build();
    assertTrue(vm.getEngine().getLanguages().containsKey(SomLanguage.LANG_ID));
  }

  @Test
  public void startEngineWithCommandLineParametersForHelloWorld() throws IOException {
    Builder builder = Launcher.createContextBuilder(new String[] {"core-lib/Hello.ns"});
    context = builder.build();

    Value result = context.eval(Launcher.START);
    assertNotNull(result);
  }

  @Test
  public void startEngineForTesting() throws IOException {
    Builder builder = Launcher.createContextBuilder(new String[] {
        "--platform", "core-lib/TestSuite/BasicInterpreterTests/Arrays.ns"});
    builder.option("SOMns.TestSelector", "testEmptyToInts");
    context = builder.build();

    // Trigger initialization of SOMns
    context.getBindings(SomLanguage.LANG_ID);

    Value result = context.eval(Launcher.START);
    assertEquals(3, (long) result.as(Long.class));
  }

  @Test
  public void executeHelloWorldWithTruffleProfiler() throws IOException {
    Builder builder = Launcher.createContextBuilder(new String[] {"core-lib/Hello.ns"});
    context = builder.build();

    CPUSampler sampler = CPUSampler.find(context.getEngine());
    Assume.assumeNotNull(sampler);
    sampler.setCollecting(true);

    Value result = context.eval(Launcher.START);
    assertNotNull(result);
    assertTrue(sampler.isCollecting());
    assertTrue(sampler.getSampleCount() > 10);
    Collection<ProfilerNode<Payload>> samples = sampler.getRootNodes();
    assertTrue(samples.iterator().next().getRootName().contains(">>#"));
  }

  @Test
  public void executeHelloWorldWithoutTruffleProfiler() throws IOException {
    Builder builder = Launcher.createContextBuilder(new String[] {"core-lib/Hello.ns"});
    context = builder.build();

    CPUSampler sampler = CPUSampler.find(context.getEngine());
    Assume.assumeNotNull(sampler);
    sampler.setCollecting(true);
    assertTrue(sampler.isCollecting());

    sampler.setCollecting(false);
    Value result = context.eval(Launcher.START);
    assertNotNull(result);
    assertFalse(sampler.isCollecting());
    assertEquals(0, sampler.getSampleCount());
  }
}
