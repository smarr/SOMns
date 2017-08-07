package som.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.api.vm.PolyglotEngine.Value;
import com.oracle.truffle.api.vm.PolyglotRuntime.Instrument;
import com.oracle.truffle.tools.ProfilerInstrument;

import som.VM;
import som.interpreter.SomLanguage;
import som.vm.VmOptions;


public class SomPolyglotTests {

  PolyglotEngine engine;

  @After
  public void resetObjectSystem() {
    VM.resetClassReferences(true);
    if (engine != null) {
      engine.dispose();
    }
  }

  @Test
  public void engineKnowsSomLanguage() {
    PolyglotEngine vm = PolyglotEngine.newBuilder().build();
    assertTrue(vm.getLanguages().containsKey(SomLanguage.MIME_TYPE));
  }

  @Test
  public void startEngineWithCommandLineParametersForHelloWorld() throws IOException {
    VmOptions options = new VmOptions(new String[] {"core-lib/Hello.ns"});
    VM vm = new VM(options, true);

    Builder builder = vm.createPolyglotBuilder();
    PolyglotEngine engine = builder.build();

    Value result = engine.eval(SomLanguage.START);
    assertNotNull(result);
    engine.dispose();
  }

  @Test
  public void startEngineForTesting() throws IOException {
    VM vm = new VM(new VmOptions(new String[] {
        "--platform", "core-lib/TestSuite/BasicInterpreterTests/Arrays.ns"},
        "testEmptyToInts"), true);
    Builder builder = vm.createPolyglotBuilder();
    engine = builder.build();

    engine.getRuntime().getInstruments().values().forEach(i -> i.setEnabled(false));

    // Trigger initialization of SOMns
    engine.getLanguages().get(SomLanguage.MIME_TYPE).getGlobalObject();
    Value result = engine.eval(SomLanguage.START);
    assertEquals((long) 3, (long) result.as(Long.class));
  }

  @Test
  public void executeHelloWorldWithTruffleProfiler() throws IOException {
    VM vm = new VM(new VmOptions(new String[] {"core-lib/Hello.ns"}), true);

    Builder builder = vm.createPolyglotBuilder();
    engine = builder.build();

    Instrument profiler = engine.getRuntime().getInstruments().get(ProfilerInstrument.ID);

    Assume.assumeNotNull(profiler);
    profiler.setEnabled(true);


    assertTrue(profiler.isEnabled());
    Value result = engine.eval(SomLanguage.START);
    assertTrue(profiler.isEnabled());
    assertNotNull(result);
  }

  @Test
  public void executeHelloWorldWithoutTruffleProfiler() throws IOException {
    VM vm = new VM(new VmOptions(new String[] {"core-lib/Hello.ns"}), true);

    Builder builder = vm.createPolyglotBuilder();
    engine = builder.build();

    Instrument profiler = engine.getRuntime().getInstruments().get(ProfilerInstrument.ID);

    Assume.assumeNotNull(profiler);

    profiler.setEnabled(true);
    assertTrue(profiler.isEnabled());
    profiler.setEnabled(false);
    Value result = engine.eval(SomLanguage.START);
    assertFalse(profiler.isEnabled());
    assertNotNull(result);
  }
}
