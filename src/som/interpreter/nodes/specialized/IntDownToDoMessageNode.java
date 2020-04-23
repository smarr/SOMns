package som.interpreter.nodes.specialized;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;

import bd.primitives.Primitive;
import som.interpreter.nodes.specialized.IntToDoMessageNode.ToDoSplzr;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import tools.asyncstacktraces.ShadowStackEntryLoad;


@GenerateNodeFactory
@Primitive(selector = "downTo:do:", noWrapper = true, disabled = true,
    specializer = ToDoSplzr.class)
public abstract class IntDownToDoMessageNode extends IntToDoMessageNode {

  @Child protected ShadowStackEntryLoad shadowStackEntryLoad = ShadowStackEntryLoad.create();

  @Override
  @Specialization(guards = "block.getMethod() == blockMethod")
  public final long doIntToDo(final VirtualFrame frame, final long receiver,
                              final long limit, final SBlock block,
                              @Cached("block.getMethod()") final SInvokable blockMethod,
                              @Cached("create(blockMethod)") final DirectCallNode valueSend) {
    return IntToByDoMessageNode.doLoop(frame, valueSend, this, receiver,
        limit, -1, block, shadowStackEntryLoad);
  }

  @Override
  @Specialization(guards = "block.getMethod() == blockMethod")
  public final long doIntToDo(final VirtualFrame frame, final long receiver,
      final double dLimit, final SBlock block,
      @Cached("block.getMethod()") final SInvokable blockMethod,
      @Cached("create(blockMethod)") final DirectCallNode valueSend) {
    return IntToByDoMessageNode.doLoop(frame, valueSend, this, receiver,
        (long) dLimit, -1, block, shadowStackEntryLoad);
  }
}
