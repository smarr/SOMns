package som.primitives;

import som.primitives.MirrorPrimsFactory.CurrentDomainPrimFactory;
import som.primitives.MirrorPrimsFactory.DomainOfPrimFactory;
import som.primitives.MirrorPrimsFactory.EvaluatedEnforcedInPrimFactory;
import som.primitives.MirrorPrimsFactory.EvaluatedInPrimFactory;
import som.primitives.MirrorPrimsFactory.SetDomainOfPrimFactory;
import som.vm.Universe;


public final class MirrorPrimitives extends Primitives {
  public MirrorPrimitives(final Universe universe) {
    super(universe);
  }

  @Override
  public void installPrimitives() {
    installClassPrimitive("domainOf:",            DomainOfPrimFactory.getInstance());
    installClassPrimitive("setDomainOf:to:",      SetDomainOfPrimFactory.getInstance());
    installClassPrimitive("evaluate:in:",         EvaluatedInPrimFactory.getInstance());
    installClassPrimitive("evaluate:enforcedIn:", EvaluatedEnforcedInPrimFactory.getInstance());
    installClassPrimitive("currentDomain",        CurrentDomainPrimFactory.getInstance());
    installClassPrimitive("executesEnforced",     null);
    installClassPrimitive("executesUnenforced",   null);
  }
}
