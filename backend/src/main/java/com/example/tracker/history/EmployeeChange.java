package com.example.tracker.history;

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

/** One append-only change-history row. Sensitive fields only ever store masked values. */
@Entity
@Table(name = "employee_change")
public class EmployeeChange {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "employee_id", nullable = false, updatable = false)
  private UUID employeeId;

  @Enumerated(EnumType.STRING)
  @Column(name = "change_type", nullable = false, updatable = false, length = 20)
  private ChangeType changeType;

  @Column(name = "field_name", updatable = false, length = 50)
  private String fieldName;

  @Column(name = "old_value", updatable = false, length = 2000)
  private String oldValue;

  @Column(name = "new_value", updatable = false, length = 2000)
  private String newValue;

  @Column(name = "changed_by", nullable = false, updatable = false)
  private UUID changedBy;

  @Column(name = "changed_at", nullable = false, updatable = false)
  private Instant changedAt;

  protected EmployeeChange() {}

  public EmployeeChange(
      UUID employeeId,
      ChangeType changeType,
      String fieldName,
      String oldValue,
      String newValue,
      UUID changedBy,
      Instant changedAt) {
    this.employeeId = employeeId;
    this.changeType = changeType;
    this.fieldName = fieldName;
    this.oldValue = oldValue;
    this.newValue = newValue;
    this.changedBy = changedBy;
    this.changedAt = changedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEmployeeId() {
    return employeeId;
  }

  public ChangeType getChangeType() {
    return changeType;
  }

  public String getFieldName() {
    return fieldName;
  }

  public String getOldValue() {
    return oldValue;
  }

  public String getNewValue() {
    return newValue;
  }

  public UUID getChangedBy() {
    return changedBy;
  }

  public Instant getChangedAt() {
    return changedAt;
  }
}
