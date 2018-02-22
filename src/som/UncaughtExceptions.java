package som;

import java.lang.Thread.UncaughtExceptionHandler;

import tools.concurrency.TracingActivityThread;


/**
 * In case an actor processing thread terminates, provide some info.
 */
public final class UncaughtExceptions implements UncaughtExceptionHandler {

  private final VM vm;

  public UncaughtExceptions(final VM vm) {
    this.vm = vm;
  }

  @Override
  public void uncaughtException(final Thread t, final Throwable e) {
    if (e instanceof ThreadDeath) {
      // Ignore those, we already signaled an error
      return;
    }
    TracingActivityThread thread = (TracingActivityThread) t;
    Output.errorPrintln("Processing failed for: "
        + thread.getActivity().toString());
    e.printStackTrace();

    vm.requestExit(2);
  }
}
