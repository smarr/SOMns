package tools.dym.nodes;

import java.math.BigInteger;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

import som.interpreter.Types;
import som.interpreter.objectstorage.ClassFactory;
import som.vmobjects.SArray.SImmutableArray;
import som.vmobjects.SArray.SMutableArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObject.SMutableObject;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import som.vmobjects.SSymbol;
import tools.dym.profiles.ReadValueProfile;
import tools.dym.profiles.ReadValueProfile.ProfileCounter;


public abstract class TypeProfileNode extends Node {
  protected final ReadValueProfile profile;

  protected TypeProfileNode(final ReadValueProfile profile) {
    this.profile = profile;
  }

  public abstract void executeProfiling(Object obj);

  protected ProfileCounter create(final Object obj) {
    return profile.createCounter(Types.getClassOf(obj).getInstanceFactory());
  }

  protected ProfileCounter create(final Object obj, final ClassFactory factory) {
    return profile.createCounter(factory);
  }

  @Specialization
  public void doLong(final long obj,
      @Cached("create(obj)") final ProfileCounter cnt) {
    cnt.inc();
  }

  @Specialization
  public void doBigInt(final BigInteger obj,
      @Cached("create(obj)") final ProfileCounter cnt) {
    cnt.inc();
  }

  @Specialization
  public void doDouble(final double obj,
      @Cached("create(obj)") final ProfileCounter cnt) {
    cnt.inc();
  }

  @Specialization
  public void doString(final String obj,
      @Cached("create(obj)") final ProfileCounter cnt) {
    cnt.inc();
  }

  @Specialization(guards = "obj")
  public void doTrue(final boolean obj,
      @Cached("create(obj)") final ProfileCounter cnt) {
    cnt.inc();
  }

  @Specialization(guards = "!obj")
  public void doFalse(final boolean obj,
      @Cached("create(obj)") final ProfileCounter cnt) {
    cnt.inc();
  }

  @Specialization
  public void doSSymbol(final SSymbol obj,
      @Cached("create(obj)") final ProfileCounter cnt) {
    cnt.inc();
  }

  // TODO: we should support different block classes here, but, well, this is
  //       not really intersting for our metrics at the moment
  @Specialization
  public void doSBlock(final SBlock obj,
      @Cached("create(obj)") final ProfileCounter cnt) {
    cnt.inc();
  }

  @Specialization
  public void doSMutableArray(final SMutableArray obj,
      @Cached("create(obj)") final ProfileCounter cnt) {
    cnt.inc();
  }

  @Specialization
  public void doSImmutableArray(final SImmutableArray obj,
      @Cached("create(obj)") final ProfileCounter cnt) {
    cnt.inc();
  }

  @Specialization(guards = "obj.getFactory() == factory", limit = "100")
  public void doSMutableObject(final SMutableObject obj,
      @Cached("obj.getFactory()") final ClassFactory factory,
      @Cached("create(obj, obj.getFactory())") final ProfileCounter cnt) {
    cnt.inc();
  }

  @Specialization(guards = "obj.getFactory() == factory", limit = "100")
  public void doSImmutableObject(final SImmutableObject obj,
      @Cached("obj.getFactory()") final ClassFactory factory,
      @Cached("create(obj, obj.getFactory())") final ProfileCounter cnt) {
    cnt.inc();
  }

  @Specialization(guards = "obj.getFactory() == factory", limit = "100")
  public void doObjWithoutFields(final SObjectWithoutFields obj,
      @Cached("obj.getFactory()") final ClassFactory factory,
      @Cached("create(obj, obj.getFactory())") final ProfileCounter cnt) {
    cnt.inc();
  }

  // TODO: remove the limit hack, and add a fallback
  @Specialization(guards = "obj.getFactory() == factory", limit = "100")
  public void doClass(final SClass obj,
      @Cached("obj.getFactory()") final ClassFactory factory,
      @Cached("create(obj, obj.getFactory())") final ProfileCounter cnt) {
    cnt.inc();
  }
}
