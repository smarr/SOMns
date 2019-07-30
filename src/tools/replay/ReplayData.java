package tools.replay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Queue;

import tools.replay.TraceParser.MessageRecord;


public class ReplayData {
  protected static abstract class EntityNode implements Comparable<EntityNode> {
    final long             entityId;
    int                    childNo;
    HashMap<Integer, Long> externalData;
    int                    ordering;
    ArrayList<EntityNode>  children;
    boolean                childrenSorted = false;

    public EntityNode(final long entityId) {
      this.entityId = entityId;
    }

    void addChild(final EntityNode child) {
      if (children == null) {
        children = new ArrayList<>();
      }

      child.childNo = children.size();
      children.add(child);
    }

    protected EntityNode getChild(final int childNo) {
      assert children != null : "Actor does not exist in trace!";
      assert children.size() > childNo : "Actor does not exist in trace!";

      if (!childrenSorted) {
        Collections.sort(children);
        childrenSorted = true;
      }

      return children.get(childNo);
    }

    @Override
    public int compareTo(final EntityNode o) {
      int i = Integer.compare(ordering, o.ordering);
      if (i == 0) {
        i = Integer.compare(childNo, o.childNo);
      }
      if (i == 0) {
        i = Long.compare(entityId, o.entityId);
      }

      return i;
    }

    protected void onContextStart(final int ordering) {}

    @Override
    public String toString() {
      return "" + entityId + ":" + childNo;
    }
  }

  /**
   * Node in actor creation hierarchy.
   */
  protected static class ActorNode extends EntityNode {

    HashMap<Integer, ArrayList<MessageRecord>> bucket1          = new HashMap<>();
    HashMap<Integer, ArrayList<MessageRecord>> bucket2          = new HashMap<>();
    Queue<MessageRecord>                       expectedMessages = new java.util.LinkedList<>();
    int                                        max              = 0;
    int                                        max2             = 0;
    ArrayList<MessageRecord>                   mailbox          = null;

    int currentOrdering;

    ActorNode(final long actorId) {
      super(actorId);
    }

    /*
     * make two buckets, one for the current 65k contexs, and one for those that we
     * encounter prematurely
     * decision where stuff goes is made based on bitset or whether the ordering byte
     * already is used in the first bucket
     * when a bucket is full, we sort the contexts and put the messages inside a queue,
     * context can then be reclaimed by GC
     */
    private void newMailbox() {
      mailbox = new ArrayList<>();

      if (bucket1.containsKey(currentOrdering)) {
        // use bucket two
        assert !bucket2.containsKey(currentOrdering);
        bucket2.put(currentOrdering, mailbox);
        max2 = Math.max(max2, currentOrdering);
        assert max2 < 0x8FFF;
      } else {
        assert !bucket2.containsKey(currentOrdering);
        bucket1.put(currentOrdering, mailbox);
        max = Math.max(max, currentOrdering);
        if (max == 0xFFFF) {
          // Bucket 1 is full, switch
          assert max2 < 0x8FFF;
          for (int i = 0; i <= max; i++) {
            if (!bucket1.containsKey(i)) {
              continue;
            }
            expectedMessages.addAll(bucket1.get(i));
          }
          bucket1.clear();
          HashMap<Integer, ArrayList<MessageRecord>> temp = bucket1;
          bucket1 = bucket2;
          bucket2 = temp;
          max = max2;
          max2 = 0;
        }
      }
    }

    protected void addMessageRecord(final MessageRecord mr) {
      if (mailbox == null) {
        newMailbox();
      }
      mailbox.add(mr);
    }

    @Override
    protected void onContextStart(final int ordering) {
      currentOrdering = ordering;
      mailbox = null;
    }

    public Queue<MessageRecord> getExpectedMessages() {

      assert bucket1.size() < 0xFFFF;
      assert bucket2.isEmpty();
      for (int i = 0; i <= max; i++) {
        if (bucket1.containsKey(i)) {
          expectedMessages.addAll(bucket1.get(i));
        } else {
          continue;
        }
      }

      for (int i = 0; i <= max2; i++) {
        if (bucket2.containsKey(i)) {
          expectedMessages.addAll(bucket2.get(i));
        } else {
          continue;
        }
      }
      return expectedMessages;
    }
  }
}
