package com.example.tracker.auth;

import com.example.tracker.account.UserAccount;
import com.example.tracker.account.UserAccountRepository;
import com.example.tracker.common.ApiException;
import com.example.tracker.common.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * FR-003b/FR-003f: password sign-in with lockout. Every failure returns the same message, so a
 * caller cannot tell whether a username exists, is locked or is disabled.
 */
@Service
public class LoginService {

  public static final int MAX_FAILURES = 5;
  public static final Duration LOCK_DURATION = Duration.ofMinutes(15);
  public static final String GENERIC_FAILURE = "Invalid username or password";

  private final UserAccountRepository accounts;
  private final PasswordEncoder encoder;
  private final Clock clock;
  private final String dummyHash;

  public LoginService(UserAccountRepository accounts, PasswordEncoder encoder, Clock clock) {
    this.accounts = accounts;
    this.encoder = encoder;
    this.clock = clock;
    this.dummyHash = encoder.encode("timing-parity-placeholder");
  }

  public UserAccount authenticate(String username, String password) {
    Instant now = clock.instant();
    String raw = password == null ? "" : password;
    var found = accounts.findByUsernameIgnoreCase(username == null ? "" : username.trim());
    if (found.isEmpty()) {
      encoder.matches(raw, dummyHash);
      throw failure();
    }
    UserAccount account = found.get();
    if (account.isLocked(now)) {
      encoder.matches(raw, dummyHash);
      throw failure();
    }
    if (!encoder.matches(raw, account.getPasswordHash())) {
      int failures = account.getFailedAttempts() + 1;
      if (failures >= MAX_FAILURES) {
        account.setLockedUntil(now.plus(LOCK_DURATION));
        account.setFailedAttempts(0);
      } else {
        account.setFailedAttempts(failures);
      }
      accounts.save(account);
      throw failure();
    }
    if (!account.isEnabled()) {
      throw failure();
    }
    account.setFailedAttempts(0);
    account.setLockedUntil(null);
    account.setLastLoginAt(now);
    return accounts.save(account);
  }

  private static ApiException failure() {
    return new ApiException(ErrorCode.UNAUTHORIZED, GENERIC_FAILURE);
  }
}
