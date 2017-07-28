package som.compiler;

import static som.compiler.Symbol.Colon;
import static som.compiler.Symbol.Comma;
import static som.compiler.Symbol.Div;
import static som.compiler.Symbol.EndBlock;
import static som.compiler.Symbol.EndTerm;
import static som.compiler.Symbol.Exit;
import static som.compiler.Symbol.Identifier;
import static som.compiler.Symbol.LCurly;
import static som.compiler.Symbol.Less;
import static som.compiler.Symbol.More;
import static som.compiler.Symbol.NewBlock;
import static som.compiler.Symbol.NewTerm;
import static som.compiler.Symbol.Or;
import static som.compiler.Symbol.Period;
import static som.compiler.Symbol.RCurly;

import som.compiler.Parser.ParseError;


public class TypeParser {

  private final Parser parser;

  public TypeParser(final Parser parser) {
    this.parser = parser;
  }

  public void parseType() throws ParseError {
    if (parser.sym == Less) {
      expectType();
    }
  }

  private void nonEmptyBlockArgList() throws ParseError {
    while (parser.sym == Colon) {
      parser.expect(Colon, null);
      typeTerm();
    }

    if (parser.sym == Or) {
      blockReturnType();
    }
  }

  private void blockReturnType() throws ParseError {
    typeExpr();
  }

  private void blockType() throws ParseError {
    parser.expect(NewBlock, null);

    if (parser.sym == Colon) {
      nonEmptyBlockArgList();
    } else if (parser.sym != EndBlock) {
      blockReturnType();
    }

    parser.expect(EndBlock, null);
  }

  private void typeArguments() throws ParseError {
    parser.expect(NewBlock, null);

    while (true) {
      typeExpr();

      if (parser.sym == Comma) {
        parser.expect(Comma, null);
      } else {
        break;
      }
    }

    parser.expect(EndBlock, null);
  }

  private void typePrimary() throws ParseError {
    parser.expect(Identifier, null);
    if (parser.sym == NewBlock) {
      typeArguments();
    }
  }

  private void typeFactor() throws ParseError {
    if (parser.sym == Identifier) {
      typePrimary();
    } else if (parser.sym == LCurly) {
      tupleType();
    } else if (parser.sym == NewBlock) {
      blockType();
    } else if (parser.sym == NewTerm) {
      parser.expect(NewTerm, null);
      typeExpr();
      parser.expect(EndTerm, null);
    }
  }

  private void typeTerm() throws ParseError {
    typeFactor();

    while (parser.sym == Identifier) {
      parser.expect(Identifier, null);
    }
  }

  private void tupleType() throws ParseError {
    parser.expect(LCurly, null);

    if (parser.sym != RCurly) {
      while (true) {
        typeExpr();

        if (parser.sym == Period) {
          parser.expect(Period, null);
        } else {
          break;
        }
      }
    }

    parser.expect(RCurly, null);
  }

  private void typeExpr() throws ParseError {
    typeTerm();

    if (parser.sym == Or) {
      parser.expect(Or, null);
      typeExpr();
    } else if (parser.sym == LCurly) {
      tupleType();
    } else if (parser.sym == Colon) {
      parser.expect(Colon, null);
      typeExpr();
    } else if (parser.sym == Div) {
      parser.expect(Div, null);
      typeExpr();
    }
  }

  private void expectType() throws ParseError {
    parser.expect(Less, null);
    typeExpr();
    parser.expect(More, null);
  }

  public void parseReturnType() throws ParseError {
    if (parser.sym == Exit) {
      parser.expect(Exit, null);
      expectType();
    }
  }
}
