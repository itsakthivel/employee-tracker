package com.example.tracker.auth;

import com.example.tracker.common.FieldErrors;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** FR-003d: at least 12 characters, at most 128, and not a commonly used password. */
@Component
public class PasswordPolicy {

  public static final int MIN_LENGTH = 12;
  public static final int MAX_LENGTH = 128;

  private final Set<String> common = new HashSet<>();

  public PasswordPolicy() {
    var resource = new ClassPathResource("common-passwords.txt");
    try (var reader =
        new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.isBlank()) {
          common.add(line.trim().toLowerCase(Locale.ROOT));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot load common-passwords.txt", e);
    }
  }

  public void validate(String field, String password) {
    var errors = new FieldErrors();
    check(errors, field, password);
    errors.throwIfAny();
  }

  public void check(FieldErrors errors, String field, String password) {
    if (password == null || password.length() < MIN_LENGTH) {
      errors.add(field, "must be at least " + MIN_LENGTH + " characters");
    } else if (password.length() > MAX_LENGTH) {
      errors.add(field, "must be at most " + MAX_LENGTH + " characters");
    } else if (common.contains(password.toLowerCase(Locale.ROOT))) {
      errors.add(field, "is too common; choose a less predictable password");
    }
  }
}
