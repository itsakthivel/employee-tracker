package com.example.tracker.common;

import java.util.Map;

/** An error with a machine-readable code, rendered as RFC 9457 Problem Details. */
public class ApiException extends RuntimeException {

  private final ErrorCode code;
  private final transient Map<String, Object> properties;

  public ApiException(ErrorCode code, String detail) {
    this(code, detail, Map.of());
  }

  public ApiException(ErrorCode code, String detail, Map<String, Object> properties) {
    super(detail);
    this.code = code;
    this.properties = properties;
  }

  public ErrorCode code() {
    return code;
  }

  public Map<String, Object> properties() {
    return properties;
  }

  public static ApiException notFound(String what) {
    return new ApiException(ErrorCode.NOT_FOUND, what + " not found");
  }
}
