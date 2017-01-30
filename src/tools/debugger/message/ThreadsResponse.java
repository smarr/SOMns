package tools.debugger.message;

import java.util.Map;
import java.util.Map.Entry;

import som.vm.Activity;
import tools.debugger.message.Message.Response;


@SuppressWarnings("unused")
public final class ThreadsResponse extends Response {
  private final Thread[] threads;

  private ThreadsResponse(final Thread[] threads, final int requestId) {
    super(requestId);
    this.threads = threads;
  }

  private static class Thread {
    private final int id;
    private final String name;

    Thread(final int id, final String name) {
      this.id = id;
      this.name = name;
    }
  }

  public static ThreadsResponse create(final Map<Activity, Integer> activities,
      final int requestId) {
    Thread[] arr = new Thread[activities.size()];
    int i = 0;
    for (Entry<Activity, Integer> e : activities.entrySet()) {
      arr[i] = new Thread(e.getValue(), e.getKey().getName());
      i += 1;
    }
    return new ThreadsResponse(arr, requestId);
  }
}
