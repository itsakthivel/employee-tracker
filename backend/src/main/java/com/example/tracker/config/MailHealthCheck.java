package com.example.tracker.config;

import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/** Checks SMTP at startup outside dev. Failure is logged, not fatal: admins can issue temporary passwords. */
@Component
@Profile("!dev & !test")
public class MailHealthCheck implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(MailHealthCheck.class);

  private final JavaMailSenderImpl mail;

  public MailHealthCheck(JavaMailSenderImpl mail) {
    this.mail = mail;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      mail.testConnection();
    } catch (MessagingException e) {
      log.error(
          "SMTP server {}:{} is not reachable; password-reset emails will fail until it is fixed",
          mail.getHost(), mail.getPort());
    }
  }
}
