package com.example.tracker.common;

import java.util.ArrayList;
import java.util.List;

/** Collects per-field validation messages; {@link #throwIfAny()} raises a 400 with all of them. */
public class FieldErrors {

  public record FieldError(String field, String message) {}

  private final List<FieldError> errors = new ArrayList<>();

  public FieldErrors add(String field, String message) {
    errors.add(new FieldError(field, message));
    return this;
  }

  public boolean isEmpty() {
    return errors.isEmpty();
  }

  public List<FieldError> list() {
    return List.copyOf(errors);
  }

  public void throwIfAny() {
    if (!errors.isEmpty()) {
      throw new ValidationException(this);
    }
  }

  public static class ValidationException extends RuntimeException {
    private final transient FieldErrors errors;

    public ValidationException(FieldErrors errors) {
      super("Validation failed");
      this.errors = errors;
    }

    public List<FieldError> errors() {
      return errors.list();
    }
  }
}
