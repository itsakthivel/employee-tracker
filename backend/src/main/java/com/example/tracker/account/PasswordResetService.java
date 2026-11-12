package com.example.tracker.account;

import com.example.tracker.auth.PasswordPolicy;
import com.example.tracker.common.ApiException;
import com.example.tracker.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-003e: single-use reset links that expire after one hour. Only token hashes are stored. */
@Service
public class PasswordResetService {

  private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
  public static final Duration TOKEN_LIFETIME = Duration.ofHours(1);

  private final UserAccountRepository accounts;
  private final PasswordResetTokenRepository tokens;
  private final PasswordEncoder encoder;
  private final PasswordPolicy policy;
  private final SessionRevoker revoker;
  private final JavaMailSender mail;
  private final Clock clock;
  private final String appBaseUrl;
  private final String mailFrom;
  private final SecureRandom random = new SecureRandom();

  public PasswordResetService(
      UserAccountRepository accounts,
      PasswordResetTokenRepository tokens,
      PasswordEncoder encoder,
      PasswordPolicy policy,
      SessionRevoker revoker,
      JavaMailSender mail,
      Clock clock,
      @Value("${tracker.app-base-url}") String appBaseUrl,
      @Value("${tracker.mail.from}") String mailFrom) {
    this.accounts = accounts;
    this.tokens = tokens;
    this.encoder = encoder;
    this.policy = policy;
    this.revoker = revoker;
    this.mail = mail;
    this.clock = clock;
    this.appBaseUrl = appBaseUrl;
    this.mailFrom = mailFrom;
  }

  /** Always completes normally, whether or not the account exists. */
  @Transactional
  public void request(String username) {
    if (username == null || username.isBlank()) {
      return;
    }
    var found = accounts.findByUsernameIgnoreCase(username.trim());
    if (found.isEmpty() || !found.get().isEnabled()) {
      return;
    }
    UserAccount account = found.get();
    Instant now = clock.instant();
    tokens.invalidateUnused(account.getId(), now);

    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    tokens.save(new PasswordResetToken(account, sha256(token), now.plus(TOKEN_LIFETIME)));

    var message = new SimpleMailMessage();
    message.setFrom(mailFrom);
    message.setTo(account.getUsername());
    message.setSubject("Reset your Employee Tracker password");
    message.setText(
        "Someone asked to reset the password for this account.\n\n"
            + "Use this link within 1 hour. It works once:\n"
            + appBaseUrl
            + "/reset-password?token="
            + token
            + "\n\nIf you did not ask for this, you can ignore this email.");
    try {
      mail.send(message);
    } catch (MailException e) {
      log.error("Password-reset email could not be sent: {}", e.getClass().getSimpleName());
    }
  }

  @Transactional
  public void confirm(String token, String newPassword) {
    Instant now = clock.instant();
    var found = token == null ? null : tokens.findByTokenHash(sha256(token)).orElse(null);
    if (found == null || !found.isUsable(now) || !found.getUser().isEnabled()) {
      throw new ApiException(
          ErrorCode.RESET_TOKEN_INVALID,
          "This link is invalid or has expired",
          Map.of(
              "fieldErrors",
              List.of(Map.of("field", "token", "message", "is invalid or has expired"))));
    }
    policy.validate("newPassword", newPassword);
    UserAccount account = found.getUser();
    account.setPassword(encoder.encode(newPassword), false);
    found.markUsed(now);
    revoker.revokeAll(account.getId());
  }

  static String sha256(String value) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
