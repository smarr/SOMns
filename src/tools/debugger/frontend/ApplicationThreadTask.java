package tools.debugger.frontend;

import tools.debugger.FrontendConnector;


/**
 * A task that needs to be executed by the application thread while it is in a
 * {@link Suspension}.
 */
abstract class ApplicationThreadTask {
  /**
   * @return continue waiting on true, resume execution on false.
   */
  abstract boolean execute();

  static class Resume extends ApplicationThreadTask {
    @Override
    public boolean execute() { return false; }
  }

  static class SendStackTrace extends ApplicationThreadTask {
    private final int startFrame;
    private final int levels;
    private final int requestId;
    private final FrontendConnector frontend;
    private final Suspension suspension;

    SendStackTrace(final int startFrame, final int levels,
        final FrontendConnector frontend, final Suspension suspension,
        final int requestId) {
      this.startFrame = startFrame;
      this.levels     = levels;
      this.frontend   = frontend;
      this.suspension = suspension;
      this.requestId  = requestId;
    }

    @Override
    public boolean execute() {
      frontend.sendStackTrace(startFrame, levels, suspension, requestId);
      return true;
    }
  }
}
