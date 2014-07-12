package som.primitives;

import som.primitives.MirrorPrimsFactory.CurrentDomainPrimFactory;
import som.primitives.MirrorPrimsFactory.DomainOfPrimFactory;
import som.primitives.MirrorPrimsFactory.EvaluatedEnforcedInPrimFactory;
import som.primitives.MirrorPrimsFactory.EvaluatedInPrimFactory;
import som.primitives.MirrorPrimsFactory.ExecutesEnforcedPrimFactory;
import som.primitives.MirrorPrimsFactory.ExecutesUnenforcedPrimFactory;
import som.primitives.MirrorPrimsFactory.SetDomainOfPrimFactory;


public final class MirrorPrimitives extends Primitives {
  @Override
  public void installPrimitives() {
    installClassPrimitive("domainOf:",            DomainOfPrimFactory.getInstance());
    installClassPrimitive("setDomainOf:to:",      SetDomainOfPrimFactory.getInstance());
    installClassPrimitive("evaluate:in:",         EvaluatedInPrimFactory.getInstance());
    installClassPrimitive("evaluate:enforcedIn:", EvaluatedEnforcedInPrimFactory.getInstance());
    installClassPrimitive("currentDomain",        CurrentDomainPrimFactory.getInstance());
    installClassPrimitive("executesEnforced",     ExecutesEnforcedPrimFactory.getInstance());
    installClassPrimitive("executesUnenforced",   ExecutesUnenforcedPrimFactory.getInstance());
  }
}
