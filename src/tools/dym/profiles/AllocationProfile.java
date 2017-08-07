package tools.dym.profiles;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.objectstorage.ClassFactory;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObject.SMutableObject;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import tools.dym.profiles.AllocationProfileFactory.AllocProfileNodeGen;


public class AllocationProfile extends Counter {

  protected final AllocProfileNode profile;

  public AllocationProfile(final SourceSection source) {
    super(source);
    this.profile = AllocProfileNodeGen.create();
  }

  public AllocProfileNode getProfile() {
    return profile;
  }

  public int getNumberOfObjectFields() {
    return profile.getNumberOfFields();
  }

  public String getTypeName() {
    return profile.getTypeName();
  }

  public abstract static class AllocProfileNode extends Node {
    protected int          numFields = -1;
    protected ClassFactory classFactory;

    public abstract void executeProfiling(Object obj);

    public int getNumberOfFields() {
      return numFields;
    }

    public String getTypeName() {
      return classFactory.getClassName().getString();
    }

    protected ClassFactory create(final ClassFactory factory) {
      int n;
      if (factory.getInstanceLayout() == null) {
        n = 0;
      } else {
        n = factory.getInstanceLayout().getNumberOfFields();
      }
      if (numFields == -1) {
        numFields = n;
        classFactory = factory;
      } else {
        assert numFields == n;
        assert classFactory.getClassName() == factory.getClassName();
      }
      return factory;
    }

    @Specialization(guards = "obj.getFactory() == factory", limit = "1")
    public void doObjectWOFields(final SObjectWithoutFields obj,
        @Cached("create(obj.getFactory())") final ClassFactory factory) {}

    @Specialization(guards = "obj.getFactory() == factory", limit = "1")
    public void doSMutableObject(final SMutableObject obj,
        @Cached("create(obj.getFactory())") final ClassFactory factory) {}

    @Specialization(guards = "obj.getFactory() == factory", limit = "1")
    public void doSImmutableObject(final SImmutableObject obj,
        @Cached("create(obj.getFactory())") final ClassFactory factory) {}
  }
}
