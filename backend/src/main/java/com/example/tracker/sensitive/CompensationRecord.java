package com.example.tracker.sensitive;

import com.example.tracker.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** One dated, append-only compensation entry (FR-006e). The amount is encrypted. */
@Entity
@Table(name = "compensation_record")
public class CompensationRecord extends BaseEntity {

  public enum PayFrequency {
    ANNUAL,
    MONTHLY,
    HOURLY
  }

  @Column(name = "employee_id", nullable = false, updatable = false)
  private UUID employeeId;

  @Column(nullable = false, updatable = false)
  private byte[] amount;

  @Column(nullable = false, updatable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(name = "pay_frequency", nullable = false, updatable = false, length = 10)
  private PayFrequency payFrequency;

  @Column(name = "effective_date", nullable = false, updatable = false)
  private LocalDate effectiveDate;

  @Column(updatable = false, length = 200)
  private String reason;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  protected CompensationRecord() {}

  CompensationRecord(
      UUID employeeId,
      byte[] amount,
      String currency,
      PayFrequency payFrequency,
      LocalDate effectiveDate,
      String reason,
      UUID createdBy) {
    this.employeeId = employeeId;
    this.amount = amount;
    this.currency = currency;
    this.payFrequency = payFrequency;
    this.effectiveDate = effectiveDate;
    this.reason = reason;
    this.createdBy = createdBy;
  }

  public UUID getEmployeeId() {
    return employeeId;
  }

  byte[] getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public PayFrequency getPayFrequency() {
    return payFrequency;
  }

  public LocalDate getEffectiveDate() {
    return effectiveDate;
  }

  public String getReason() {
    return reason;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }
}
