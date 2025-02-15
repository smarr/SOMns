package tools.dym;

import com.oracle.truffle.api.utilities.JSONHelper.JSONStringBuilder;


public interface JsonSerializable {
  JSONStringBuilder toJson();
}
