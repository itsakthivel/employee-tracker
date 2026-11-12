package com.example.tracker.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
  NOT_FOUND(HttpStatus.NOT_FOUND),
  FORBIDDEN(HttpStatus.FORBIDDEN),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
  DUPLICATE_EMPLOYEE_NUMBER(HttpStatus.CONFLICT),
  DUPLICATE_EMAIL(HttpStatus.CONFLICT),
  DUPLICATE_NAME(HttpStatus.CONFLICT),
  STALE_VERSION(HttpStatus.CONFLICT),
  REPORTING_CYCLE(HttpStatus.UNPROCESSABLE_ENTITY),
  INVALID_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY),
  END_BEFORE_START(HttpStatus.UNPROCESSABLE_ENTITY),
  DIRECT_REPORTS(HttpStatus.CONFLICT),
  IN_USE(HttpStatus.CONFLICT),
  ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED),
  PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN),
  RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST),
  ACCOUNT_EXISTS(HttpStatus.CONFLICT),
  EMPLOYEE_TERMINATED(HttpStatus.CONFLICT),
  SELF_LOCKOUT(HttpStatus.CONFLICT),
  LAST_ADMIN(HttpStatus.CONFLICT),
  DUPLICATE_EFFECTIVE_DATE(HttpStatus.CONFLICT),
  SENSITIVE_DATA_UNAVAILABLE(HttpStatus.INTERNAL_SERVER_ERROR);

  private final HttpStatus status;

  ErrorCode(HttpStatus status) {
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
