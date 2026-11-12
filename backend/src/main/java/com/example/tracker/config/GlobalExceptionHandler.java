package com.example.tracker.config;

import com.example.tracker.common.ApiException;
import com.example.tracker.common.ErrorCode;
import com.example.tracker.common.FieldErrors;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ProblemDetail api(ApiException e) {
    ProblemDetail p = problem(e.code(), e.getMessage());
    e.properties().forEach(p::setProperty);
    return p;
  }

  @ExceptionHandler(FieldErrors.ValidationException.class)
  ProblemDetail validation(FieldErrors.ValidationException e) {
    return validationProblem(e.errors());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail beanValidation(MethodArgumentNotValidException e) {
    return validationProblem(
        e.getBindingResult().getFieldErrors().stream()
            .map(f -> new FieldErrors.FieldError(f.getField(), f.getDefaultMessage()))
            .toList());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail unreadable(HttpMessageNotReadableException e) {
    if (e.getCause() instanceof UnrecognizedPropertyException u) {
      return validationProblem(
          List.of(new FieldErrors.FieldError(u.getPropertyName(), "cannot be changed here")));
    }
    if (e.getCause() instanceof InvalidFormatException f) {
      return validationProblem(List.of(new FieldErrors.FieldError(path(f), "has an invalid value")));
    }
    if (e.getCause() instanceof MismatchedInputException m) {
      return validationProblem(List.of(new FieldErrors.FieldError(path(m), "has an invalid value")));
    }
    return validationProblem(List.of(new FieldErrors.FieldError("body", "is not valid JSON")));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail typeMismatch(MethodArgumentTypeMismatchException e) {
    return validationProblem(List.of(new FieldErrors.FieldError(e.getName(), "has an invalid value")));
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ProblemDetail stale(ObjectOptimisticLockingFailureException e) {
    return problem(
        ErrorCode.STALE_VERSION, "This record was changed by someone else. Reload and try again.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail integrity(DataIntegrityViolationException e) {
    String msg = String.valueOf(e.getMostSpecificCause().getMessage());
    if (msg.contains("ux_employee_number")) {
      return problem(ErrorCode.DUPLICATE_EMPLOYEE_NUMBER, "Employee ID already in use");
    }
    if (msg.contains("ux_employee_email") || msg.contains("ux_user_username")) {
      return problem(ErrorCode.DUPLICATE_EMAIL, "Work email already in use");
    }
    if (msg.contains("ux_department_name") || msg.contains("ux_location_name")) {
      return problem(ErrorCode.DUPLICATE_NAME, "Name already in use");
    }
    if (msg.contains("ux_compensation_effective")) {
      return problem(
          ErrorCode.DUPLICATE_EFFECTIVE_DATE, "A compensation record already exists for that date");
    }
    log.warn("Data integrity violation: {}", e.getClass().getSimpleName());
    return problem(ErrorCode.IN_USE, "The change conflicts with existing data");
  }

  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail denied(AccessDeniedException e) {
    return problem(ErrorCode.FORBIDDEN, "You do not have access to this");
  }

  private static ProblemDetail validationProblem(List<FieldErrors.FieldError> errors) {
    ProblemDetail p = problem(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid");
    p.setProperty(
        "fieldErrors",
        errors.stream()
            .map(f -> Map.of("field", f.field(), "message", f.message()))
            .collect(Collectors.toList()));
    return p;
  }

  private static ProblemDetail problem(ErrorCode code, String detail) {
    HttpStatus status = code.status();
    ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
    p.setTitle(status.getReasonPhrase());
    p.setProperty("code", code.name());
    return p;
  }

  private static String path(MismatchedInputException e) {
    return e.getPath().stream()
        .map(r -> r.getFieldName() != null ? r.getFieldName() : "[" + r.getIndex() + "]")
        .collect(Collectors.joining("."));
  }
}
