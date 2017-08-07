package som.interpreter.nodes.literals;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.ExpressionNode;
import som.primitives.arrays.ArraySetAllStrategy;
import som.vm.constants.Classes;
import som.vmobjects.SArray;
import som.vmobjects.SArray.SMutableArray;
import tools.dym.Tags.NewArray;


public abstract class ArrayLiteralNode extends LiteralNode {
  public static ArrayLiteralNode create(final ExpressionNode[] exprs,
      final SourceSection source) {
    return new Uninit(exprs, source);
  }

  @Children protected final ExpressionNode[] expressions;

  protected ArrayLiteralNode(final ExpressionNode[] expressions, final SourceSection source) {
    super(source);
    assert source != null;
    this.expressions = expressions;
  }

  @Override
  protected boolean isTaggedWith(final Class<?> tag) {
    if (tag == NewArray.class) {
      return true;
    } else {
      return super.isTaggedWith(tag);
    }
  }

  private static final class Uninit extends ArrayLiteralNode {
    private Uninit(final ExpressionNode[] expressions, final SourceSection source) {
      super(expressions, source);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      Object storage =
          ArraySetAllStrategy.evaluateFirstDetermineStorageAndEvaluateRest(frame, expressions);
      SMutableArray arr = new SMutableArray(storage, Classes.arrayClass);
      specialize(arr);
      return arr;
    }

    private void specialize(final SMutableArray storage) {
      if (storage.isBooleanType()) {
        replace(new Booleans(expressions, sourceSection));
      } else if (storage.isDoubleType()) {
        replace(new Doubles(expressions, sourceSection));
      } else if (storage.isEmptyType()) {
        replace(new Empty(expressions, sourceSection));
      } else if (storage.isLongType()) {
        replace(new Longs(expressions, sourceSection));
      } else {
        assert storage.isObjectType() : "Partially empty is not supported yet. Should be simple to add.";
        replace(new Objects(expressions, sourceSection));
      }
    }
  }

  private abstract static class Specialized extends ArrayLiteralNode {
    private Specialized(final ExpressionNode[] expressions, final SourceSection source) {
      super(expressions, source);
    }

    protected abstract Object executeSpecialized(VirtualFrame frame);

    protected abstract boolean expectedType(SMutableArray arr);

    @Override
    public final Object executeGeneric(final VirtualFrame frame) {
      Object storage = executeSpecialized(frame);
      SMutableArray result = new SMutableArray(storage, Classes.arrayClass);
      if (!expectedType(result)) {
        replace(new Objects(expressions, sourceSection));
      }
      return result;
    }
  }

  private static final class Booleans extends Specialized {
    private Booleans(final ExpressionNode[] expressions, final SourceSection source) {
      super(expressions, source);
    }

    @Override
    protected Object executeSpecialized(final VirtualFrame frame) {
      return ArraySetAllStrategy.evalForRemaining(frame, expressions,
          new boolean[expressions.length], SArray.FIRST_IDX);
    }

    @Override
    protected boolean expectedType(final SMutableArray arr) {
      return arr.isBooleanType();
    }
  }

  private static final class Doubles extends Specialized {
    private Doubles(final ExpressionNode[] expressions, final SourceSection source) {
      super(expressions, source);
    }

    @Override
    protected Object executeSpecialized(final VirtualFrame frame) {
      return ArraySetAllStrategy.evalForRemaining(frame, expressions,
          new double[expressions.length], SArray.FIRST_IDX);
    }

    @Override
    protected boolean expectedType(final SMutableArray arr) {
      return arr.isDoubleType();
    }
  }

  private static final class Longs extends Specialized {
    private Longs(final ExpressionNode[] expressions, final SourceSection source) {
      super(expressions, source);
    }

    @Override
    protected Object executeSpecialized(final VirtualFrame frame) {
      return ArraySetAllStrategy.evalForRemaining(frame, expressions,
          new long[expressions.length], SArray.FIRST_IDX);
    }

    @Override
    protected boolean expectedType(final SMutableArray arr) {
      return arr.isLongType();
    }
  }

  private static final class Empty extends Specialized {
    private Empty(final ExpressionNode[] expressions, final SourceSection source) {
      super(expressions, source);
    }

    @Override
    protected Object executeSpecialized(final VirtualFrame frame) {
      return ArraySetAllStrategy.evalForRemainingNils(frame, expressions,
          SArray.FIRST_IDX);
    }

    @Override
    protected boolean expectedType(final SMutableArray arr) {
      return arr.isEmptyType();
    }
  }

  private static final class Objects extends Specialized {
    private Objects(final ExpressionNode[] expressions, final SourceSection source) {
      super(expressions, source);
    }

    @Override
    protected Object executeSpecialized(final VirtualFrame frame) {
      return ArraySetAllStrategy.evalForRemaining(frame, expressions,
          new Object[expressions.length], SArray.FIRST_IDX);
    }

    @Override
    protected boolean expectedType(final SMutableArray arr) {
      return true;
    }
  }
}
