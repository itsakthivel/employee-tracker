package com.example.tracker.sensitive;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only record of every reveal of, and change to, sensitive data (FR-006d). */
@Entity
@Table(name = "sensitive_access_log")
public class SensitiveAccessLog {

  public enum Action {
    REVEAL,
    CREATE,
    UPDATE
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "actor_id", nullable = false, updatable = false)
  private UUID actorId;

  @Column(name = "employee_id", nullable = false, updatable = false)
  private UUID employeeId;

  @Column(nullable = false, updatable = false, length = 30)
  private String field;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 10)
  private Action action;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  protected SensitiveAccessLog() {}

  SensitiveAccessLog(UUID actorId, UUID employeeId, String field, Action action, Instant occurredAt) {
    this.actorId = actorId;
    this.employeeId = employeeId;
    this.field = field;
    this.action = action;
    this.occurredAt = occurredAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getActorId() {
    return actorId;
  }

  public UUID getEmployeeId() {
    return employeeId;
  }

  public String getField() {
    return field;
  }

  public Action getAction() {
    return action;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
