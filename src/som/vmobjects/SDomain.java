package som.vmobjects;


public final class SDomain {

  public static final int NEW_OBJECT_DOMAIN_IDX = 0;
  private static final int NUM_SDOMAIN_FIELDS = NEW_OBJECT_DOMAIN_IDX + 1;

  public static SObject createStandardDomain(final SObject nilObject) {
    SObject domain = SObject.create(nilObject, nilObject, NUM_SDOMAIN_FIELDS);
    domain.setDomain(domain);
    setDomainForNewObjects(domain, domain);
    return domain;
  }

  public static SObject getDomainForNewObjects(final SObject domain) {
    return domain.getSDomainDomainForNewObjects();
  }

  public static void setDomainForNewObjects(final SObject domain, final SObject newObjectDomain) {
    domain.setSDomainDomainForNewObjects(newObjectDomain);
  }
}
