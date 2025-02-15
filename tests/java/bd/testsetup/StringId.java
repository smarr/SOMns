package bd.testsetup;

import bd.basic.IdProvider;


public class StringId implements IdProvider<String> {
  @Override
  public String getId(final String id) {
    return id.intern();
  }
}
