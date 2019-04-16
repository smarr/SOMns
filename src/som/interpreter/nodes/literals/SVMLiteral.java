package som.interpreter.nodes.literals;

import com.oracle.truffle.api.frame.VirtualFrame;

import som.interpreter.SomLanguage;


public class SVMLiteral extends LiteralNode {

  private SomLanguage language;

  public SVMLiteral(final SomLanguage language) {
    this.language = language;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return language.getVM().getVmMirror();
  }

}
