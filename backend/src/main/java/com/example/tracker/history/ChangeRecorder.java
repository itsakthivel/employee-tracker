package com.example.tracker.history;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes change history in the caller's transaction, so a change and its history commit together. */
@Component
public class ChangeRecorder {

  private final EmployeeChangeRepository changes;
  private final Clock clock;

  public ChangeRecorder(EmployeeChangeRepository changes, Clock clock) {
    this.changes = changes;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordCreated(UUID employeeId, UUID actorId) {
    changes.save(
        new EmployeeChange(employeeId, ChangeType.CREATED, null, null, null, actorId, clock.instant()));
  }

  /** Writes one row per changed field; returns how many rows were written. */
  @Transactional(propagation = Propagation.MANDATORY)
  public int recordDiff(
      UUID employeeId,
      Map<String, String> before,
      Map<String, String> after,
      ChangeType type,
      UUID actorId) {
    var diff = EmployeeSnapshot.diff(before, after);
    var now = clock.instant();
    diff.forEach(
        (field, values) ->
            changes.save(
                new EmployeeChange(employeeId, type, field, values[0], values[1], actorId, now)));
    return diff.size();
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordField(
      UUID employeeId,
      String field,
      String oldDisplay,
      String newDisplay,
      ChangeType type,
      UUID actorId) {
    changes.save(
        new EmployeeChange(employeeId, type, field, oldDisplay, newDisplay, actorId, clock.instant()));
  }
}
