package som.vmobjects;


public final class SDomain {

  public static SObject createStandardDomain(final SObject nilObject) {
    SObject domain = SObject.create(nilObject, nilObject, 1);
    domain.setDomain(domain);
    setDomainForNewObjects(domain, domain);
    return domain;
  }

  public static SObject getDomainForNewObjects(final SObject domain) {
    return domain..
  }

  public static void setDomainForNewObjects(final SObject domain, final SObject newObjectDomain) {
    domain.. = newObjectDomain;
  }
}
