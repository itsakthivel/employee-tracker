package com.example.tracker.account;

import java.util.UUID;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/** Ends a user's sessions. Sessions are indexed by account id (see CurrentUser#getName). */
@Component
public class SessionRevoker {

  private final FindByIndexNameSessionRepository<? extends Session> sessions;

  public SessionRevoker(FindByIndexNameSessionRepository<? extends Session> sessions) {
    this.sessions = sessions;
  }

  public void revokeAll(UUID accountId) {
    revokeAllExcept(accountId, null);
  }

  public void revokeAllExcept(UUID accountId, String keepSessionId) {
    sessions.findByPrincipalName(accountId.toString()).keySet().stream()
        .filter(id -> !id.equals(keepSessionId))
        .forEach(sessions::deleteById);
  }
}
