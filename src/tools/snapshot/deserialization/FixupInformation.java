package tools.snapshot.deserialization;

import java.util.Iterator;


public abstract class FixupInformation {

  public abstract void fixUp(Object o);

  protected FixupInformation next;

  public static class FixupList implements Iterable<FixupInformation> {
    FixupInformation head;
    FixupInformation tail;

    public FixupList(final FixupInformation initial) {
      head = initial;
      tail = initial;
    }

    public void add(final FixupInformation fi) {
      tail.next = fi;
      tail = fi;
    }

    @Override
    public Iterator<FixupInformation> iterator() {
      return new Iterator<FixupInformation>() {
        FixupInformation next = head;

        @Override
        public FixupInformation next() {
          assert next != null;
          FixupInformation res = next;
          next = next.next;
          return res;
        }

        @Override
        public boolean hasNext() {
          return next != null;
        }
      };
    }
  }
}
