package som.vmobjects;

import som.vm.constants.Domain;
import som.vm.constants.Nil;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.utilities.BranchProfile;


public final class SDomain {

  public static final int NEW_OBJECT_DOMAIN_IDX = 0;
  public static final int NUM_SDOMAIN_FIELDS = NEW_OBJECT_DOMAIN_IDX + 1;

  public static SObject createStandardDomain() {
    SObject domain = SObject.create(Nil.nilObject, NUM_SDOMAIN_FIELDS);
    domain.setDomain(domain);
    setDomainForNewObjects(domain, domain);
    return domain;
  }

  public static void completeStandardDomainInitialization(final SObject standardDomain) {
    standardDomain.setField(0, standardDomain);
  }

  public static SObject getDomainForNewObjects(final SObject domain) {
    return domain.getSDomainDomainForNewObjects();
  }

  public static void setDomainForNewObjects(final SObject domain, final SObject newObjectDomain) {
    domain.setSDomainDomainForNewObjects(newObjectDomain);
  }

  public static SObject getOwner(final Object o) {
    CompilerAsserts.neverPartOfCompilation();

    if (o instanceof SAbstractObject) {
      return ((SAbstractObject) o).getDomain();
    } else if (o instanceof Object[]) {
      return SArray.getOwner((Object[]) o);
    } else {
      return Domain.standard;
    }
  }

  public static final class GetOwnerNode {

    private final BranchProfile obj  = new BranchProfile();
    private final BranchProfile arr  = new BranchProfile();
    private final BranchProfile prim = new BranchProfile();

    public SObject getOwner(final Object o) {
      if (o instanceof SAbstractObject) {
        obj.enter();
        return ((SAbstractObject) o).getDomain();
      } else if (o instanceof Object[]) {
        arr.enter();
        return SArray.getOwner((Object[]) o);
      } else {
        prim.enter();
        return Domain.standard;
      }
    }
  }
}
