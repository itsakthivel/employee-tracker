package com.example.tracker.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the first administrator from environment variables when none exists. */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

  private final UserAccountRepository accounts;
  private final PasswordEncoder encoder;
  private final String email;
  private final String password;

  public BootstrapAdminRunner(
      UserAccountRepository accounts,
      PasswordEncoder encoder,
      @Value("${TRACKER_BOOTSTRAP_ADMIN_EMAIL:}") String email,
      @Value("${TRACKER_BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
    this.accounts = accounts;
    this.encoder = encoder;
    this.email = email;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (email.isBlank() || password.isBlank()) {
      return;
    }
    if (accounts.countByRoleAndEnabledTrue(Role.ADMIN) > 0) {
      log.warn("TRACKER_BOOTSTRAP_ADMIN_* is set but an administrator already exists; remove it.");
      return;
    }
    var admin = new UserAccount(email, encoder.encode(password), Role.ADMIN, null);
    admin.setMustChangePassword(true);
    accounts.save(admin);
    log.warn("Created bootstrap administrator. Remove TRACKER_BOOTSTRAP_ADMIN_* from the environment.");
  }
}
