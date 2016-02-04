package som.compiler;


public class Tags {
  public static final String ROOT_TAG = "ROOT";
  public static final String UNSPECIFIED_INVOKE = "UNSPECIFIED_INVOKE"; // this is some form of invoke in the source, unclear what it is during program execution

  public static final String NEW_OBJECT = "NEW_OBJECT";
  public static final String NEW_ARRAY  = "NEW_ARRAY";

  public static final String CONTROL_FLOW_CONDITION  = "CONTROL_FLOW_CONDITION"; // a condition expression that results in a control-flow change

  public static final String FIELD_READ         = "FIELD_READ";
  public static final String FIELD_WRITE        = "FIELD_WRITE";

  public static final String LOCAL_VAR_READ     = "LOCAL_VAR_READ";
  public static final String LOCAL_VAR_WRITE    = "LOCAL_VAR_WRITE";
  public static final String LOCAL_ARG_READ     = "LOCAL_ARG_READ";

  public static final String ARRAY_READ         = "ARRAY_READ";
  public static final String ARRAY_WRITE        = "ARRAY_WRITE";
  public static final String LOOP_BODY          = "LOOP_BODY";

  // Syntax annotations
  public static final String SYNTAX_KEYWORD = "SYNTAX_KEYWORD";
  public static final String SYNTAX_LITERAL = "SYNTAX_LITERAL";
  public static final String SYNTAX_COMMENT = "SYNTAX_COMMENT";
  public static final String SYNTAX_IDENTIFIER = "SYNTAX_IDENTIFIER";
  public static final String SYNTAX_ARGUMENT = "SYNTAX_ARGUMENT";
  public static final String SYNTAX_LOCAL_VARIABLE = "SYNTAX_LOCAL_VARIABLE";
}
