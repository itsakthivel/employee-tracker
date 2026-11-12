package com.example.tracker.sensitive;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes the access log in the caller's transaction, so no access goes unlogged (SC-008). */
@Component
class SensitiveAccessLogger {

  private final SensitiveAccessLogRepository log;
  private final Clock clock;

  SensitiveAccessLogger(SensitiveAccessLogRepository log, Clock clock) {
    this.log = log;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  void log(UUID actorId, UUID employeeId, String field, SensitiveAccessLog.Action action) {
    log.save(new SensitiveAccessLog(actorId, employeeId, field, action, clock.instant()));
  }
}
