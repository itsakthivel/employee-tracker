package com.example.tracker.security;

import static com.example.tracker.support.ContractAssert.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tracker.account.Role;
import com.example.tracker.support.AbstractIntegrationTest;
import com.example.tracker.support.Client;
import java.time.Duration;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuthSecurityIT extends AbstractIntegrationTest {

  private static final String NEW_PASSWORD = "Another-Long-Secret-42";

  @Test
  void unknownUserAndWrongPasswordGetTheSameAnswer() throws Exception {
    account("alice@example.com", Role.ADMIN, null);
    var unknown = body(client().login("nobody@example.com", PASSWORD).andExpect(status().isUnauthorized()).andExpect(matchesContract()));
    var wrong = body(client().login("alice@example.com", "wrong-password-123").andExpect(status().isUnauthorized()));
    assertThat(unknown.path("detail").asText()).isEqualTo(wrong.path("detail").asText());
    assertThat(unknown.path("code").asText()).isEqualTo(wrong.path("code").asText());
  }

  @Test
  void loginReturnsCurrentUserAndKeysSessionByAccountId() throws Exception {
    var account = account("alice@example.com", Role.ADMIN, null);
    Client c = client();
    c.login("ALICE@example.com", PASSWORD)
        .andExpect(status().isOk())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.role").value("ADMIN"))
        .andExpect(jsonPath("$.mustChangePassword").value(false));
    c.get("/api/v1/auth/me").andExpect(status().isOk()).andExpect(matchesContract());
    var principals = jdbc.queryForList("select principal_name from spring_session", String.class);
    assertThat(principals).containsExactly(account.getId().toString());
    var timeout = jdbc.queryForObject("select max_inactive_interval from spring_session", Integer.class);
    assertThat(timeout).isEqualTo(1800);
  }

  @Test
  void locksAfterFiveFailuresForFifteenMinutes() throws Exception {
    account("alice@example.com", Role.ADMIN, null);
    for (int i = 0; i < 5; i++) {
      client().login("alice@example.com", "wrong-password-123").andExpect(status().isUnauthorized());
    }
    client().login("alice@example.com", PASSWORD).andExpect(status().isUnauthorized());
    clock().advance(Duration.ofMinutes(14));
    client().login("alice@example.com", PASSWORD).andExpect(status().isUnauthorized());
    clock().advance(Duration.ofMinutes(2));
    client().login("alice@example.com", PASSWORD).andExpect(status().isOk());
  }

  @Test
  void successResetsTheFailureCounter() throws Exception {
    account("alice@example.com", Role.ADMIN, null);
    for (int i = 0; i < 4; i++) {
      client().login("alice@example.com", "wrong-password-123");
    }
    client().login("alice@example.com", PASSWORD).andExpect(status().isOk());
    for (int i = 0; i < 4; i++) {
      client().login("alice@example.com", "wrong-password-123");
    }
    client().login("alice@example.com", PASSWORD).andExpect(status().isOk());
  }

  @Test
  void disabledAccountCannotSignIn() throws Exception {
    var a = account("alice@example.com", Role.ADMIN, null);
    a.setEnabled(false);
    accounts.save(a);
    client().login("alice@example.com", PASSWORD).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Invalid username or password"));
  }

  @Test
  void passwordPolicyRejectsShortAndCommonPasswords() throws Exception {
    account("alice@example.com", Role.ADMIN, null);
    Client c = signedIn("alice@example.com");
    c.post("/api/v1/auth/change-password", change(PASSWORD, "Short-pw-11"))
        .andExpect(status().isBadRequest())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
    c.post("/api/v1/auth/change-password", change(PASSWORD, "Password1234"))
        .andExpect(status().isBadRequest());
    c.post("/api/v1/auth/change-password", change("not-my-password", NEW_PASSWORD))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void temporaryPasswordMustBeChangedFirst() throws Exception {
    var a = account("alice@example.com", Role.ADMIN, null);
    a.setMustChangePassword(true);
    accounts.save(a);
    Client c = signedIn("alice@example.com");
    c.get("/api/v1/users")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    c.get("/api/v1/auth/me").andExpect(jsonPath("$.mustChangePassword").value(true));
    c.post("/api/v1/auth/change-password", change(PASSWORD, NEW_PASSWORD))
        .andExpect(status().isNoContent());
    c.get("/api/v1/users").andExpect(status().isOk());
  }

  @Test
  void changingPasswordEndsOtherSessions() throws Exception {
    account("alice@example.com", Role.ADMIN, null);
    Client a = signedIn("alice@example.com");
    Client b = signedIn("alice@example.com");
    a.post("/api/v1/auth/change-password", change(PASSWORD, NEW_PASSWORD))
        .andExpect(status().isNoContent());
    a.get("/api/v1/auth/me").andExpect(status().isOk());
    b.get("/api/v1/auth/me").andExpect(status().isUnauthorized());
  }

  @Test
  void resetLinkWorksOnceAndClearsLockout() throws Exception {
    account("alice@example.com", Role.ADMIN, null);
    for (int i = 0; i < 5; i++) {
      client().login("alice@example.com", "wrong-password-123");
    }
    Client existing = signedInOrNull();
    client()
        .post("/api/v1/auth/password-reset", "{\"username\":\"alice@example.com\"}")
        .andExpect(status().isAccepted());
    String token = tokenFromLatestEmail("alice@example.com");

    client().post("/api/v1/auth/password-reset/confirm", confirm(token, NEW_PASSWORD))
        .andExpect(status().isNoContent());
    client().post("/api/v1/auth/password-reset/confirm", confirm(token, NEW_PASSWORD))
        .andExpect(status().isBadRequest())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    client().login("alice@example.com", NEW_PASSWORD).andExpect(status().isOk());
    assertThat(existing).isNull();
  }

  @Test
  void resetLinkExpiresAfterOneHour() throws Exception {
    account("alice@example.com", Role.ADMIN, null);
    client().post("/api/v1/auth/password-reset", "{\"username\":\"alice@example.com\"}");
    String token = tokenFromLatestEmail("alice@example.com");
    clock().advance(Duration.ofMinutes(61));
    client().post("/api/v1/auth/password-reset/confirm", confirm(token, NEW_PASSWORD))
        .andExpect(status().isBadRequest());
  }

  @Test
  void resetForUnknownUserLooksTheSameAndSendsNothing() throws Exception {
    client()
        .post("/api/v1/auth/password-reset", "{\"username\":\"nobody@example.com\"}")
        .andExpect(status().isAccepted());
    assertThat(mailMessages()).isEmpty();
  }

  @Test
  void mutatingRequestsNeedTheCsrfToken() throws Exception {
    account("alice@example.com", Role.ADMIN, null);
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice@example.com\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void logoutEndsTheSession() throws Exception {
    account("alice@example.com", Role.ADMIN, null);
    Client c = signedIn("alice@example.com");
    c.post("/api/v1/auth/logout", "{}").andExpect(status().isNoContent());
    c.get("/api/v1/auth/me").andExpect(status().isUnauthorized());
  }

  private Client signedInOrNull() {
    return null;
  }

  private String tokenFromLatestEmail(String to) throws Exception {
    var messages = mailMessages();
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0).path("To").get(0).path("Address").asText()).isEqualTo(to);
    String text = mailText(messages.get(0).path("ID").asText());
    var m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(text);
    assertThat(m.find()).isTrue();
    return m.group(1);
  }

  private static String change(String current, String next) {
    return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
  }

  private static String confirm(String token, String password) {
    return "{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}";
  }
}
