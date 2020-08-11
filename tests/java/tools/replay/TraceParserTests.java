/**
 *
 */
package tools.replay;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import som.vm.VmSettings;
import tools.replay.ReplayData.EntityNode;


@RunWith(Parameterized.class)
public class TraceParserTests {

  private static final String[] TRACE_NAMES = new String[] {
      "val-messages",
      "val-messages-2",
      "fj-throughput"};

  private static final Object[] EXPECTED_RESULTS = new Object[] {
      new Object[] {9, 120, 60},
      new Object[] {33, 3412, 3197},
      new Object[] {6, 120, 15}
  };

  private static final int[] BUFFER_SIZES =
      new int[] {64, 1024, 64 * 1024, VmSettings.BUFFER_SIZE};

  @Parameters(name = "{0} {1} [{index}]")
  public static Iterable<Object[]> data() {
    List<Object[]> list = new ArrayList<>();

    for (int i = 0; i < TRACE_NAMES.length; i += 1) {
      for (int bufferSize : BUFFER_SIZES) {
        list.add(new Object[] {TRACE_NAMES[i], bufferSize, EXPECTED_RESULTS[i]});
      }
    }
    return list;
  }

  private final TraceParser parser;

  private final int expectedEntities;
  private final int expectedReplayEvents;
  private final int expectedSubtraces;

  public TraceParserTests(final String test, final int bufferSize,
      final Object[] expectedResults) {
    String folder = "tests/replay/" + test + "/";
    String traceName = folder + "trace";
    parser = new TraceParser(traceName, bufferSize);
    this.expectedEntities = (int) expectedResults[0];
    this.expectedReplayEvents = (int) expectedResults[1];
    this.expectedSubtraces = (int) expectedResults[2];
  }

  @Before
  public void setUp() {
    parser.initialize();
  }

  @After
  public void tearDown() throws IOException {
    parser.close();
  }

  @Test
  public void testInitialParseTrace() {
    assertEquals(expectedEntities, parser.getEntities().size());
  }

  @Test
  public void testGetEntities() {
    int numReplayEvents = 0;
    int numSubtraces = 0;

    for (Entry<Long, EntityNode> e : parser.getEntities().entrySet()) {
      while (parser.getMoreEventsForEntity(e.getKey())) {
        // NOOP
      }

      numReplayEvents += parser.getReplayEventsForEntity(e.getKey()).size();
      numSubtraces += parser.getNumberOfSubtraces(e.getKey());
    }

    assertEquals(expectedReplayEvents, numReplayEvents);
    assertEquals(expectedSubtraces, numSubtraces);
  }

}
