package com.example.tracker.sensitive;

import com.example.tracker.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

/** Government ID, tax ID and bank details. Encrypted columns hold FieldCipher output. */
@Entity
@Table(name = "employee_sensitive")
public class EmployeeSensitive extends BaseEntity {

  @Column(name = "employee_id", nullable = false, updatable = false)
  private UUID employeeId;

  @Column(name = "gov_id_type", length = 50)
  String govIdType;

  @Column(name = "gov_id_number")
  byte[] govIdNumber;

  @Column(name = "gov_id_last4", length = 4)
  String govIdLast4;

  @Column(name = "tax_id")
  byte[] taxId;

  @Column(name = "tax_id_last4", length = 4)
  String taxIdLast4;

  @Column(name = "bank_account_holder")
  byte[] bankAccountHolder;

  @Column(name = "bank_name", length = 150)
  String bankName;

  @Column(name = "bank_account_number")
  byte[] bankAccountNumber;

  @Column(name = "bank_account_last4", length = 4)
  String bankAccountLast4;

  @Column(name = "bank_routing_code")
  byte[] bankRoutingCode;

  @Column(name = "bank_routing_last4", length = 4)
  String bankRoutingLast4;

  @Column(name = "key_id", nullable = false, length = 40)
  String keyId;

  @Version private long version;

  protected EmployeeSensitive() {}

  EmployeeSensitive(UUID employeeId) {
    this.employeeId = employeeId;
  }

  public UUID getEmployeeId() {
    return employeeId;
  }

  public long getVersion() {
    return version;
  }

  @Override
  public String toString() {
    return "EmployeeSensitive[employeeId=" + employeeId + "]";
  }
}
