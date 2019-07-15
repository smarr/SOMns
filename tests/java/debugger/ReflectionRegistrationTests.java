package debugger;

import static org.junit.Assert.assertEquals;

import java.util.HashSet;

import org.junit.Test;

import tools.debugger.RuntimeReflectionRegistration;


public class ReflectionRegistrationTests {

  @Test
  public void runtimeRegistration() {
    RuntimeReflectionRegistration rrr = new RuntimeReflectionRegistration();
    rrr.beforeAnalysis(null);

    HashSet<Class<?>> registeredClasses = rrr.getRegisteredClasses();
    assertEquals(56, registeredClasses.size());
  }
}
