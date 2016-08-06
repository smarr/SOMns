package tools.dym.profiles;

import som.interpreter.objectstorage.ClassFactory;
import tools.dym.profiles.ReadValueProfile.ProfileCounter;


public interface CreateCounter {
  ProfileCounter createCounter(ClassFactory factory);
}
