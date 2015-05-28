package som.interpreter.nodes;

import som.compiler.ClassBuilder.ClassDefinitionId;


public interface ISpecialSend {
  boolean isSuperSend();
  ClassDefinitionId getLexicalClass();
}
